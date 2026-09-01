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
  private final Clock clock;

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
    this.contextBuilder = contextBuilder;
    this.decisionEngine = decisionEngine;
    this.strategyValidator = strategyValidator;
    this.orderService = orderService;
    this.executionGateway = executionGateway;
    this.reconciliationService = reconciliationService;
    this.executionStore = executionStore;
    this.audit = audit;
    this.clock = clock;
  }

  public AutonomousExecutionResult runCycle(UUID botId) {
    Instant now = clock.instant();
    audit.record("AGENT", botId.toString(), "AUTONOMOUS_CYCLE_STARTED", "BOT", botId.toString(), Map.of());

    // 1. Build Context
    TradingContext context = contextBuilder.buildContext(botId);

    // 2. LLM Decision Analysis
    StructuredTradeDecision decision = decisionEngine.analyze(context);

    // 3. Check for NO_ACTION / HOLD / FAILED
    if (decision.validationStatus() != ValidationStatus.VALIDATED ||
        decision.decision() == TradeAction.NO_ACTION || decision.decision() == TradeAction.HOLD) {
      UUID intentId = UUID.randomUUID();
      AutonomousExecutionResult noActionResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "NO_ACTION_TAKEN", "Decision resulted in " + decision.decision().name(), now
      );
      return noActionResult;
    }

    // 4. Create and persist ValidatedTradeIntent
    UUID intentId = UUID.randomUUID();
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        intentId, decision.id(), context.contextId(), context.contextHash(), botId,
        context.agentSessionId(),
        context.strategy() != null ? context.strategy().strategyId() : UUID.randomUUID(),
        decision.strategyVersionId(), decision.symbol(), decision.decision(),
        decision.side(), decision.quantity(), decision.referencePrice(), decision.stopLoss(),
        decision.takeProfit(), decision.timeHorizon(), now, decision.expiresAt()
    );
    executionStore.saveIntent(intent);

    // 5. Strategy Validation
    StrategyValidationResult valResult = strategyValidator.validate(intent, context);
    if (!valResult.passed()) {
      AutonomousExecutionResult stratFailResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "REJECTED_STRATEGY", valResult.reason(), now
      );
      executionStore.saveExecution(stratFailResult);
      return stratFailResult;
    }

    // 6. Mode Gating: OBSERVE_ONLY vs PAPER_AUTONOMOUS / DEMO_AUTONOMOUS
    if (context.autonomousMode() == AutonomousMode.OBSERVE_ONLY) {
      AutonomousExecutionResult observeResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, null,
          "OBSERVE_ONLY_RECORDED", "Observation recorded: Would execute " + intent.action() + " " + intent.quantity() + " " + intent.symbol(), now
      );
      executionStore.saveExecution(observeResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_OBSERVE_ONLY", "INTENT", intentId.toString(),
          Map.of("action", intent.action().name(), "symbol", intent.symbol()));
      return observeResult;
    }

    // 7. Deterministic Risk Evaluation & Order Creation
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
        now,
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
          "REJECTED_RISK", e.getMessage() != null ? e.getMessage() : "RISK_REJECTED", now
      );
      executionStore.saveExecution(riskRejectResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_RISK_REJECTED", "INTENT", intentId.toString(), Map.of("reason", e.getMessage()));
      return riskRejectResult;
    }

    // 8. Order Dispatch via ExecutionGateway
    audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_STARTED", "ORDER", createdOrder.id().toString(), Map.of());
    OrderSubmissionResult submissionResult;
    try {
      submissionResult = executionGateway.dispatch(createdOrder.id());
    } catch (Exception e) {
      log.error("Autonomous order dispatch failed for bot {}: {}", botId, e.getMessage(), e);
      AutonomousExecutionResult dispatchFailResult = new AutonomousExecutionResult(
          UUID.randomUUID(), intentId, decision.id(), null, createdOrder.id(),
          "FAILED_BROKER", e.getMessage() != null ? e.getMessage() : "BROKER_DISPATCH_FAILURE", now
      );
      executionStore.saveExecution(dispatchFailResult);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_FAILED", "ORDER", createdOrder.id().toString(), Map.of("error", e.getMessage()));
      return dispatchFailResult;
    }

    // 9. Reconcile Post-Execution
    try {
      if (reconciliationService != null) {
        reconciliationService.reconcile(botId.toString());
      }
    } catch (Exception recEx) {
      log.warn("Post-execution reconciliation triggered warning for bot {}: {}", botId, recEx.getMessage());
    }

    AutonomousExecutionResult successResult = new AutonomousExecutionResult(
        UUID.randomUUID(), intentId, decision.id(), null, createdOrder.id(),
        "EXECUTED", "Order successfully dispatched: status=" + submissionResult.status() + " exchangeOrderId=" + submissionResult.exchangeOrderId(), now
    );
    executionStore.saveExecution(successResult);

    audit.record("AGENT", botId.toString(), "AUTONOMOUS_EXECUTION_COMPLETED", "ORDER", createdOrder.id().toString(),
        Map.of("exchangeOrderId", submissionResult.exchangeOrderId() != null ? submissionResult.exchangeOrderId() : "", "status", submissionResult.status().name()));

    return successResult;
  }
}
