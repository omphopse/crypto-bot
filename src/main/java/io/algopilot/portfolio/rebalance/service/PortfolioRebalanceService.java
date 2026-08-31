package io.algopilot.portfolio.rebalance.service;

import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderRejectedException;
import io.algopilot.order.OrderService;
import io.algopilot.portfolio.allocation.model.AllocationWeight;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import io.algopilot.portfolio.rebalance.model.PortfolioDriftResult;
import io.algopilot.portfolio.rebalance.model.RebalanceOrder;
import io.algopilot.portfolio.rebalance.model.RebalanceOrderIntent;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import io.algopilot.portfolio.rebalance.persistence.RebalanceStore;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioRebalanceService {
  private static final Logger log = LoggerFactory.getLogger(PortfolioRebalanceService.class);

  private final io.algopilot.bot.BotStore botStore;
  private final PortfolioAllocationStore allocationStore;
  private final RebalanceStore rebalanceStore;
  private final OrderService orderService;
  private final ExecutionGateway executionGateway;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public PortfolioRebalanceService(
      PortfolioAllocationStore allocationStore,
      RebalanceStore rebalanceStore,
      OrderService orderService,
      ExecutionGateway executionGateway,
      AuditEventWriter audit,
      io.algopilot.bot.BotStore botStore) {
    this(allocationStore, rebalanceStore, orderService, executionGateway, audit, botStore, Clock.systemUTC());
  }

  public PortfolioRebalanceService(
      PortfolioAllocationStore allocationStore,
      RebalanceStore rebalanceStore,
      OrderService orderService,
      ExecutionGateway executionGateway,
      AuditEventWriter audit,
      io.algopilot.bot.BotStore botStore,
      Clock clock) {
    this.allocationStore = allocationStore;
    this.rebalanceStore = rebalanceStore;
    this.orderService = orderService;
    this.executionGateway = executionGateway;
    this.audit = audit;
    this.botStore = botStore;
    this.clock = clock;
  }

  public PortfolioDriftResult evaluateDrift(
      PortfolioAllocationPlan plan,
      Map<String, BigDecimal> currentHoldingsValue,
      BigDecimal driftThresholdPct
  ) {
    if (plan == null) {
      throw new IllegalArgumentException("PORTFOLIO_PLAN_REQUIRED");
    }
    BigDecimal threshold = driftThresholdPct != null ? driftThresholdPct : new BigDecimal("5.00");

    BigDecimal totalCurrentVal = BigDecimal.ZERO;
    if (currentHoldingsValue != null) {
      for (BigDecimal val : currentHoldingsValue.values()) {
        if (val != null && val.signum() > 0) {
          totalCurrentVal = totalCurrentVal.add(val);
        }
      }
    }
    if (totalCurrentVal.signum() <= 0) {
      totalCurrentVal = plan.totalCapital();
    }

    BigDecimal maxDrift = BigDecimal.ZERO;
    Map<String, BigDecimal> symbolDrifts = new HashMap<>();
    List<RebalanceOrderIntent> proposedOrders = new ArrayList<>();

    for (AllocationWeight weight : plan.targetWeights()) {
      String symbol = weight.symbol();
      BigDecimal holdingVal = currentHoldingsValue != null ? currentHoldingsValue.getOrDefault(symbol, BigDecimal.ZERO) : BigDecimal.ZERO;
      BigDecimal currentWeight = holdingVal.divide(totalCurrentVal, 4, RoundingMode.HALF_UP);
      BigDecimal targetWeight = weight.targetWeight();

      BigDecimal driftPct = currentWeight.subtract(targetWeight).abs().multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
      symbolDrifts.put(symbol, driftPct);

      if (driftPct.compareTo(maxDrift) > 0) {
        maxDrift = driftPct;
      }

      if (driftPct.compareTo(threshold) >= 0) {
        BigDecimal targetVal = totalCurrentVal.multiply(targetWeight).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deltaVal = targetVal.subtract(holdingVal);

        String side = deltaVal.signum() > 0 ? "BUY" : "SELL";
        BigDecimal absDelta = deltaVal.abs();
        BigDecimal estimatedPrice = symbol.startsWith("BTC") ? new BigDecimal("60000.00") : (symbol.startsWith("ETH") ? new BigDecimal("3000.00") : new BigDecimal("100.00"));
        BigDecimal quantity = absDelta.divide(estimatedPrice, 8, RoundingMode.HALF_UP);

        proposedOrders.add(new RebalanceOrderIntent(
            symbol,
            side,
            quantity,
            estimatedPrice,
            absDelta,
            "Rebalance drift " + driftPct.toPlainString() + "% exceeds threshold " + threshold.toPlainString() + "%"
        ));
      }
    }

    boolean rebalanceRequired = maxDrift.compareTo(threshold) >= 0 && !proposedOrders.isEmpty();
    return new PortfolioDriftResult(
        plan.id(),
        rebalanceRequired,
        maxDrift,
        symbolDrifts,
        proposedOrders,
        clock.instant()
    );
  }

  public RebalanceRun executeRebalance(
      UUID planId,
      UUID botId,
      Map<String, BigDecimal> currentHoldingsValue,
      BigDecimal driftThresholdPct
  ) {
    PortfolioAllocationPlan plan = allocationStore.findPlanById(planId)
        .orElseThrow(() -> new IllegalArgumentException("PLAN_NOT_FOUND: " + planId));

    PortfolioDriftResult drift = evaluateDrift(plan, currentHoldingsValue, driftThresholdPct);
    UUID runId = UUID.randomUUID();
    Instant now = clock.instant();

    RebalanceRun run = new RebalanceRun(
        runId,
        planId,
        "STARTED",
        drift.maxDriftPct(),
        drift.proposedOrders().size(),
        0,
        0,
        "Rebalance triggered with max drift " + drift.maxDriftPct() + "%",
        now,
        null
    );
    rebalanceStore.saveRun(run);

    audit.record(
        "PORTFOLIO_REBALANCE",
        "SYSTEM",
        "PORTFOLIO_REBALANCE_STARTED",
        "REBALANCE_RUN",
        runId.toString(),
        Map.of(
            "planId", planId.toString(),
            "botId", botId.toString(),
            "ordersCount", drift.proposedOrders().size(),
            "maxDriftPct", drift.maxDriftPct().toPlainString()
        )
    );

    int executed = 0;
    int failed = 0;

    io.algopilot.bot.Bot targetBot = (botStore != null) ? botStore.findById(botId).orElse(null) : null;
    String stratVerId = (targetBot != null && targetBot.strategyVersionId() != null) ? targetBot.strategyVersionId().toString() : UUID.randomUUID().toString();

    for (RebalanceOrderIntent intent : drift.proposedOrders()) {
      String clientOrderId = "reb-" + runId.toString().substring(0, 8) + "-" + intent.symbol().replace("/", "").toLowerCase() + "-" + System.currentTimeMillis();

      // Submit through OrderService which enforces RiskDecisionService evaluate()
      RiskDecisionRequest riskReq = new RiskDecisionRequest(
          clientOrderId,
          botId.toString(),
          stratVerId,
          intent.symbol(),
          RiskDecisionRequest.Side.valueOf(intent.side()),
          intent.quantity(),
          intent.estimatedPrice(),
          plan.totalCapital(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          new BigDecimal("0.05"),
          new BigDecimal("0.05"),
          now,
          false,
          false,
          false,
          0,
          0,
          0
      );

      try {
        OrderRecord order = orderService.create(riskReq);
        executionGateway.dispatch(order.id());
        executed++;

        rebalanceStore.saveOrder(new RebalanceOrder(
            UUID.randomUUID(),
            runId,
            botId,
            intent.symbol(),
            intent.side(),
            intent.quantity(),
            intent.estimatedPrice(),
            order.id(),
            "EXECUTED",
            now
        ));
      } catch (OrderRejectedException rej) {
        log.warn("Rebalance order rejected by RiskDecisionService: {}", rej.getMessage());
        failed++;
        rebalanceStore.saveOrder(new RebalanceOrder(
            UUID.randomUUID(),
            runId,
            botId,
            intent.symbol(),
            intent.side(),
            intent.quantity(),
            intent.estimatedPrice(),
            null,
            "REJECTED_BY_RISK",
            now
        ));
      } catch (Exception ex) {
        log.error("Rebalance order dispatch failed", ex);
        failed++;
        rebalanceStore.saveOrder(new RebalanceOrder(
            UUID.randomUUID(),
            runId,
            botId,
            intent.symbol(),
            intent.side(),
            intent.quantity(),
            intent.estimatedPrice(),
            null,
            "EXECUTION_FAILED",
            now
        ));
      }
    }

    String finalStatus = failed == 0 ? "COMPLETED" : (executed > 0 ? "PARTIALLY_COMPLETED" : "FAILED_RISK_GATING");
    Instant completedAt = clock.instant();

    RebalanceRun updatedRun = new RebalanceRun(
        runId,
        planId,
        finalStatus,
        drift.maxDriftPct(),
        drift.proposedOrders().size(),
        executed,
        failed,
        "Rebalance execution finished with status " + finalStatus,
        now,
        completedAt
    );
    rebalanceStore.updateRun(updatedRun);

    audit.record(
        "PORTFOLIO_REBALANCE",
        "SYSTEM",
        "PORTFOLIO_REBALANCE_COMPLETED",
        "REBALANCE_RUN",
        runId.toString(),
        Map.of(
            "status", finalStatus,
            "executedCount", executed,
            "failedCount", failed
        )
    );

    return updatedRun;
  }

  public List<RebalanceRun> getRecentRuns(int limit) {
    return rebalanceStore.findRecentRuns(limit);
  }

  public Optional<RebalanceRun> getRun(UUID id) {
    return rebalanceStore.findRunById(id);
  }

  public List<RebalanceOrder> getRunOrders(UUID runId) {
    return rebalanceStore.findOrdersByRunId(runId);
  }
}
