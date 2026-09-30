package io.algopilot.agent.execution;

import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.adapter.OrderSubmissionResult;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.LLMDecisionEngineService;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.decision.ValidationStatus;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.market.observation.MarketObservation;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderService;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AutonomousExecutionOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(AutonomousExecutionOrchestrator.class);

  private final ContextBuilderService contextBuilder;
  private final LLMDecisionEngineService decisionEngine;
  private final StrategyValidationService strategyValidator;
  private final OrderService orderService;
  private final ExecutionGateway executionGateway;
  private final ReconciliationService reconciliationService;
  private final AutonomousExecutionStore executionStore;
  private final AuditEventWriter audit;
  private final DeterministicPositionSizer positionSizer;
  private final ExecutionMarketDataFreshnessService freshnessService;
  private final io.algopilot.fill.FillIngestionService fillIngestionService;
  private final io.algopilot.adapter.alpaca.AlpacaPaperAdapter alpacaAdapter;
  private final io.algopilot.portfolio.PositionStore positionStore;
  private final Clock clock;

  @Autowired
  public AutonomousExecutionOrchestrator(
      ContextBuilderService contextBuilder,
      LLMDecisionEngineService decisionEngine,
      StrategyValidationService strategyValidator,
      OrderService orderService,
      ExecutionGateway executionGateway,
      ReconciliationService reconciliationService,
      AutonomousExecutionStore executionStore,
      AuditEventWriter audit,
      @Autowired(required = false) DeterministicPositionSizer positionSizer,
      @Autowired(required = false) ExecutionMarketDataFreshnessService freshnessService,
      @Autowired(required = false) io.algopilot.fill.FillIngestionService fillIngestionService,
      @Autowired(required = false) io.algopilot.adapter.alpaca.AlpacaPaperAdapter alpacaAdapter,
      @Autowired(required = false) io.algopilot.portfolio.PositionStore positionStore,
      @Autowired(required = false) Clock clock
  ) {
    this.contextBuilder = contextBuilder;
    this.decisionEngine = decisionEngine;
    this.strategyValidator = strategyValidator;
    this.orderService = orderService;
    this.executionGateway = executionGateway;
    this.reconciliationService = reconciliationService;
    this.executionStore = executionStore;
    this.audit = audit;
    this.positionSizer = positionSizer != null ? positionSizer : new DeterministicPositionSizer();
    this.freshnessService = freshnessService;
    this.fillIngestionService = fillIngestionService;
    this.alpacaAdapter = alpacaAdapter;
    this.positionStore = positionStore;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public AutonomousExecutionOrchestrator(
      ContextBuilderService contextBuilder,
      LLMDecisionEngineService decisionEngine,
      StrategyValidationService strategyValidator,
      OrderService orderService,
      ExecutionGateway executionGateway,
      ReconciliationService reconciliationService,
      AutonomousExecutionStore executionStore,
      AuditEventWriter audit,
      @Autowired(required = false) DeterministicPositionSizer positionSizer,
      @Autowired(required = false) ExecutionMarketDataFreshnessService freshnessService,
      @Autowired(required = false) io.algopilot.fill.FillIngestionService fillIngestionService,
      @Autowired(required = false) io.algopilot.adapter.alpaca.AlpacaPaperAdapter alpacaAdapter,
      @Autowired(required = false) Clock clock
  ) {
    this(contextBuilder, decisionEngine, strategyValidator, orderService, executionGateway,
        reconciliationService, executionStore, audit, positionSizer, freshnessService, fillIngestionService, alpacaAdapter, null, clock);
  }

  public AutonomousExecutionOrchestrator(
      ContextBuilderService contextBuilder,
      LLMDecisionEngineService decisionEngine,
      StrategyValidationService strategyValidator,
      OrderService orderService,
      ExecutionGateway executionGateway,
      ReconciliationService reconciliationService,
      AutonomousExecutionStore executionStore,
      AuditEventWriter audit,
      Clock clock
  ) {
    this(contextBuilder, decisionEngine, strategyValidator, orderService, executionGateway,
        reconciliationService, executionStore, audit, null, null, null, null, null, clock);
  }

  public AutonomousExecutionResult runCycle(UUID botId) {
    Instant cycleStart = clock.instant();
    audit.record("AGENT", botId.toString(), "AUTONOMOUS_CYCLE_STARTED", "BOT", botId.toString(), Map.of());

    // 1. Build Context
    TradingContext context = contextBuilder.buildContext(botId);

    // 2. LLM Decision Analysis (takes ~25-30s for local Ollama)
    StructuredTradeDecision decision = decisionEngine.analyze(context);

    // 3. Check for NO_ACTION / HOLD / FAILED
    if (decision.validationStatus() != ValidationStatus.VALIDATED ||
        decision.decision() == TradeAction.NO_ACTION || decision.decision() == TradeAction.HOLD) {
      UUID intentId = UUID.randomUUID();
      AutonomousExecutionResult noActionResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "NO_ACTION_TAKEN", "Decision resulted in " + decision.decision().name(), clock.instant()
      );
      return noActionResult;
    }

    // 4. Actionable Signal: Fresh Market Data & Deterministic Execution Sizing
    Instant executionNow = clock.instant();
    MarketObservation freshObs;
    if (freshnessService != null) {
      freshObs = freshnessService.getFreshMarketData(decision.symbol(), context);
    } else if (context.market() != null) {
      freshObs = new MarketObservation(
          UUID.randomUUID(), decision.symbol(), context.market().provider(), context.market().environment(),
          context.market().lastPrice(), context.market().bid(), context.market().ask(),
          context.market().spread(), context.market().volume(), context.market().openPrice(),
          context.market().highPrice(), context.market().lowPrice(), context.market().closePrice(),
          context.market().timeframe(), executionNow, executionNow, false, 0L, "VALID"
      );
    } else {
      BigDecimal fallbackPrice = decision.referencePrice() != null ? decision.referencePrice() : new BigDecimal("78200.00");
      freshObs = new MarketObservation(
          UUID.randomUUID(), decision.symbol(), "ALPACA_PAPER", "PAPER",
          fallbackPrice, fallbackPrice, fallbackPrice, BigDecimal.ZERO, BigDecimal.ONE,
          fallbackPrice, fallbackPrice, fallbackPrice, fallbackPrice, "1m",
          executionNow, executionNow, false, 0L, "VALID"
      );
    }

    BigDecimal freshPrice = freshObs.lastPrice();
    Instant freshTimestamp = freshObs.marketTimestamp();

    // Deterministic Execution Sizing
    BigDecimal equity = (context.portfolio() != null && context.portfolio().portfolioEquity() != null)
        ? context.portfolio().portfolioEquity() : new BigDecimal("100.00");
    BigDecimal buyingPower = (context.portfolio() != null && context.portfolio().cash() != null)
        ? context.portfolio().cash() : equity;
    BigDecimal maxPosPct = new BigDecimal("10.0");

    // Find held position for this symbol
    BigDecimal heldPositionQty = BigDecimal.ZERO;
    if (context.positions() != null) {
      for (var pos : context.positions()) {
        if (pos.symbol() != null && pos.symbol().equalsIgnoreCase(decision.symbol())) {
          heldPositionQty = pos.quantity() != null ? pos.quantity() : BigDecimal.ZERO;
          break;
        }
      }
    }

    BigDecimal executionQty;
    if (decision.decision() == TradeAction.BUY) {
      executionQty = positionSizer.calculateBuyQuantity(
          decision.symbol(), freshPrice, equity, buyingPower, maxPosPct, new BigDecimal("0.00001")
      );
    } else if (decision.decision() == TradeAction.SELL || decision.decision() == TradeAction.CLOSE || decision.decision() == TradeAction.REDUCE) {
      executionQty = positionSizer.calculateExitQuantity(
          decision.symbol(), decision.decision(), heldPositionQty, decision.quantity()
      );
      if (executionQty.compareTo(BigDecimal.ZERO) <= 0) {
        log.warn("Autonomous execution rejected exit for bot {}: no held position to exit", botId);
        UUID intentId = UUID.randomUUID();
        AutonomousExecutionResult noPosResult = new AutonomousExecutionResult(
            UUID.randomUUID(), intentId, decision.id(), null, null,
            "REJECTED_STRATEGY", "POSITION_NOT_FOUND_FOR_EXIT", executionNow
        );
        executionStore.saveExecution(noPosResult);
        return noPosResult;
      }
    } else {
      executionQty = decision.quantity();
    }

    log.info("DETERMINISTIC_SIZING_APPLIED botId={} symbol={} action={} llmQty={} executionQty={} freshPrice={}",
        botId, decision.symbol(), decision.decision(), decision.quantity(), executionQty, freshPrice);
    audit.record("AGENT", botId.toString(), "DETERMINISTIC_SIZING_APPLIED", "DECISION", decision.id().toString(),
        Map.of("llmProposedQty", decision.quantity() != null ? decision.quantity().toPlainString() : "0",
               "executionQty", executionQty.toPlainString(),
               "freshPrice", freshPrice.toPlainString()));

    // 5. Create and persist ValidatedTradeIntent
    UUID intentId = UUID.randomUUID();
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        intentId, decision.id(), context.contextId(), context.contextHash(), botId,
        context.agentSessionId(),
        context.strategy() != null ? context.strategy().strategyId() : UUID.randomUUID(),
        decision.strategyVersionId(), decision.symbol(), decision.decision(),
        decision.side(), executionQty, freshPrice, decision.stopLoss(),
        decision.takeProfit(), decision.timeHorizon(), executionNow, executionNow.plusSeconds(300)
    );
    executionStore.saveIntent(intent);

    // 6. Strategy Validation
    StrategyValidationResult valResult = strategyValidator.validate(intent, context);
    if (!valResult.passed()) {
      AutonomousExecutionResult stratFailResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "REJECTED_STRATEGY", valResult.reason(), executionNow
      );
      executionStore.saveExecution(stratFailResult);
      return stratFailResult;
    }

    // 7. Mode Gating: OBSERVE_ONLY vs PAPER_AUTONOMOUS / DEMO_AUTONOMOUS
    if (context.autonomousMode() == AutonomousMode.OBSERVE_ONLY) {
      AutonomousExecutionResult observeResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "OBSERVE_ONLY_RECORDED", "Observation recorded: Would execute " + intent.action() + " " + intent.quantity() + " " + intent.symbol(), executionNow
      );
      executionStore.saveExecution(observeResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_OBSERVE_ONLY", "INTENT", intentId.toString(),
          Map.of("action", intent.action().name(), "symbol", intent.symbol()));
      return observeResult;
    }

    // 8. Deterministic Risk Evaluation & Order Creation
    RiskDecisionRequest.Side riskSide = intent.action() == TradeAction.BUY ? RiskDecisionRequest.Side.BUY : RiskDecisionRequest.Side.SELL;
    String clientOrderId = "auto-" + intentId.toString().substring(0, 8);

    RiskDecisionRequest riskReq = new RiskDecisionRequest(
        clientOrderId,
        botId.toString(),
        intent.strategyVersionId().toString(),
        intent.symbol(),
        riskSide,
        intent.quantity(),
        intent.referencePrice(),
        context.portfolio() != null ? context.portfolio().portfolioEquity() : BigDecimal.ZERO,
        context.risk() != null ? context.risk().singleSymbolExposure() : BigDecimal.ZERO,
        context.risk() != null ? context.risk().portfolioExposure() : BigDecimal.ZERO,
        context.risk() != null ? context.risk().dailyLoss() : BigDecimal.ZERO,
        context.risk() != null ? context.risk().currentDrawdown() : BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        freshTimestamp,
        false,
        false,
        false,
        context.positions().size(),
        0,
        0
    );

    audit.record("AGENT", botId.toString(), "AUTONOMOUS_RISK_CHECK_STARTED", "INTENT", intentId.toString(), Map.of());
    OrderRecord createdOrder;
    try {
      createdOrder = orderService.create(riskReq);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_RISK_APPROVED", "ORDER", createdOrder.id().toString(), Map.of());
    } catch (Exception e) {
      log.warn("Autonomous risk check rejected order for bot {}: {}", botId, e.getMessage());
      AutonomousExecutionResult riskRejectResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "REJECTED_RISK", e.getMessage() != null ? e.getMessage() : "RISK_REJECTED", executionNow
      );
      executionStore.saveExecution(riskRejectResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_RISK_REJECTED", "INTENT", intentId.toString(), Map.of("reason", e.getMessage()));
      return riskRejectResult;
    }

    // 9. Order Dispatch via ExecutionGateway
    audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_STARTED", "ORDER", createdOrder.id().toString(), Map.of());
    OrderSubmissionResult submissionResult;
    try {
      submissionResult = executionGateway.dispatch(createdOrder.id());
    } catch (Exception e) {
      log.error("Autonomous order dispatch failed for bot {}: {}", botId, e.getMessage(), e);
      AutonomousExecutionResult dispatchFailResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, createdOrder.id(),
          "FAILED_BROKER", e.getMessage() != null ? e.getMessage() : "BROKER_DISPATCH_FAILURE", executionNow
      );
      executionStore.saveExecution(dispatchFailResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_FAILED", "ORDER", createdOrder.id().toString(), Map.of("error", e.getMessage()));
      return dispatchFailResult;
    }

    // 10. Ingest fill if order was immediately filled on broker
    if (fillIngestionService != null && alpacaAdapter != null && submissionResult.exchangeOrderId() != null) {
      try {
        for (int i = 0; i < 4; i++) {
          var brokerOrder = alpacaAdapter.getOrderStatus(createdOrder.clientOrderId(), submissionResult.exchangeOrderId());
          if (brokerOrder.isPresent() && (brokerOrder.get().status() == io.algopilot.order.OrderStatus.FILLED || brokerOrder.get().status() == io.algopilot.order.OrderStatus.PARTIALLY_FILLED)) {
            String exchangeFillId = null;
            BigDecimal filledQty = null;
            BigDecimal filledPrice = null;
            BigDecimal fee = BigDecimal.ZERO;

            for (int f = 0; f < 5; f++) {
              try {
                var fills = alpacaAdapter.fetchFills(io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId.toString(), clock.instant().minusSeconds(120));
                if (fills != null) {
                  for (var bf : fills) {
                    if (submissionResult.exchangeOrderId().equals(bf.brokerOrderId())) {
                      exchangeFillId = bf.exchangeFillId();
                      filledPrice = bf.price();
                      filledQty = bf.quantity();
                      fee = bf.fee() != null ? bf.fee() : BigDecimal.ZERO;
                      break;
                    }
                  }
                }
                if (exchangeFillId != null) break;
              } catch (Exception ignored) {}
              Thread.sleep(400);
            }

            if (exchangeFillId == null) {
              exchangeFillId = "alpaca-fill-" + submissionResult.exchangeOrderId();
            }
            if (filledQty == null) {
              filledQty = brokerOrder.get().filledQuantity() != null && brokerOrder.get().filledQuantity().compareTo(BigDecimal.ZERO) > 0
                  ? brokerOrder.get().filledQuantity() : createdOrder.quantity();
            }
            if (filledPrice == null) {
              filledPrice = brokerOrder.get().price() != null && brokerOrder.get().price().compareTo(BigDecimal.ZERO) > 0
                  ? brokerOrder.get().price() : freshPrice;
            }

            io.algopilot.fill.FillReport report = new io.algopilot.fill.FillReport(createdOrder.id(), exchangeFillId, filledQty, filledPrice, fee);
            fillIngestionService.ingest(report);
            log.info("AUTONOMOUS_FILL_INGESTED orderId={} exchangeOrderId={} exchangeFillId={} qty={} price={}",
                createdOrder.id(), submissionResult.exchangeOrderId(), exchangeFillId, filledQty, filledPrice);

            // Reconcile local position with broker delivery quantity to account for taker fees deducted from delivery
            if (positionStore != null) {
              try {
                var brokerPositions = alpacaAdapter.fetchPositions(io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId.toString());
                for (var bp : brokerPositions) {
                  if (bp.symbol().equals(decision.symbol())) {
                    var localPosOpt = positionStore.find(botId.toString(), decision.symbol());
                    if (localPosOpt.isPresent()) {
                      var lp = localPosOpt.get();
                      positionStore.save(new io.algopilot.portfolio.Position(
                          lp.id(), lp.botId(), lp.symbol(),
                          bp.quantity(), filledPrice, lp.realizedPnl(), clock.instant()
                      ));
                      log.info("LOCAL_POSITION_RECONCILED_WITH_BROKER_DELIVERY botId={} symbol={} qty={} avgPrice={}",
                          botId, decision.symbol(), bp.quantity(), filledPrice);
                    }
                    break;
                  }
                }
              } catch (Exception syncEx) {
                log.warn("Post-fill delivery sync notice for order {}: {}", createdOrder.id(), syncEx.getMessage());
              }
            }
            break;
          }
          Thread.sleep(250);
        }
      } catch (Exception fillEx) {
        log.warn("Post-dispatch fill ingestion notice for order {}: {}", createdOrder.id(), fillEx.getMessage());
      }
    }

    // 11. Reconcile Post-Execution
    try {
      if (reconciliationService != null) {
        reconciliationService.reconcile(botId.toString());
      }
    } catch (Exception recEx) {
      log.warn("Post-execution reconciliation triggered warning for bot {}: {}", botId, recEx.getMessage());
    }

    AutonomousExecutionResult successResult = new AutonomousExecutionResult(
        UUID.randomUUID(), intentId, decision.id(), null, createdOrder.id(),
        "EXECUTED", "Order successfully dispatched: status=" + submissionResult.status() + " exchangeOrderId=" + submissionResult.exchangeOrderId(), executionNow
    );
    executionStore.saveExecution(successResult);

    audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_COMPLETED", "ORDER", createdOrder.id().toString(),
        Map.of("exchangeOrderId", submissionResult.exchangeOrderId() != null ? submissionResult.exchangeOrderId() : "", "status", submissionResult.status().name()));

    return successResult;
  }
}
