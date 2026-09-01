package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.adapter.OrderSubmissionResult;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.FreshnessSummary;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.ReconciliationContext;
import io.algopilot.agent.context.RiskContext;
import io.algopilot.agent.context.SafetySummary;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.LLMDecisionEngineService;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.decision.ValidationStatus;
import io.algopilot.agent.execution.AutonomousExecutionOrchestrator;
import io.algopilot.agent.execution.AutonomousExecutionResult;
import io.algopilot.agent.execution.AutonomousExecutionStore;
import io.algopilot.agent.execution.StrategyValidationResult;
import io.algopilot.agent.execution.StrategyValidationService;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderService;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EndToEndForensicTraceabilityTest {
  private ContextBuilderService contextBuilder;
  private LLMDecisionEngineService decisionEngine;
  private StrategyValidationService strategyValidator;
  private OrderService orderService;
  private ExecutionGateway executionGateway;
  private ReconciliationService reconciliationService;
  private AutonomousExecutionStore executionStore;
  private AuditEventWriter audit;
  private Clock clock;
  private AutonomousExecutionOrchestrator orchestrator;

  private UUID botId;
  private UUID contextId;
  private UUID decisionId;
  private UUID stratVerId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    contextBuilder = mock(ContextBuilderService.class);
    decisionEngine = mock(LLMDecisionEngineService.class);
    strategyValidator = mock(StrategyValidationService.class);
    orderService = mock(OrderService.class);
    executionGateway = mock(ExecutionGateway.class);
    reconciliationService = mock(ReconciliationService.class);
    executionStore = mock(AutonomousExecutionStore.class);
    audit = mock(AuditEventWriter.class);

    orchestrator = new AutonomousExecutionOrchestrator(
        contextBuilder, decisionEngine, strategyValidator, orderService,
        executionGateway, reconciliationService, executionStore, audit, clock
    );

    botId = UUID.randomUUID();
    contextId = UUID.randomUUID();
    decisionId = UUID.randomUUID();
    stratVerId = UUID.randomUUID();
  }

  @Test
  void testCompleteCausalTraceability_fromContextToExecutionAndReconciliation() {
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    TradingContext context = new TradingContext(
        contextId, "hash-abc-123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.DECIDING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, null, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    StructuredTradeDecision decision = new StructuredTradeDecision(
        decisionId, contextId, "hash-abc-123", botId, UUID.randomUUID(), stratVerId,
        "fake-llm", "mock-reasoner-v1", TradeAction.BUY, "BTC/USD", "FLAT", new BigDecimal("0.85"),
        new BigDecimal("60000.00"), BigDecimal.ONE, new BigDecimal("58800.00"), new BigDecimal("62400.00"),
        "INTRADAY", "Forensic validation thesis", List.of(), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 120L, 800, 150, new BigDecimal("0.000625"), now, now.plusSeconds(300)
    );
    when(decisionEngine.analyze(context)).thenReturn(decision);
    when(strategyValidator.validate(any(), any())).thenReturn(new StrategyValidationResult(UUID.randomUUID(), UUID.randomUUID(), decision.id(), true, "VALID", now));

    UUID orderId = UUID.randomUUID();
    OrderRecord order = new OrderRecord(
        orderId, "auto-ord-1", botId.toString(), stratVerId.toString(),
        "BTC/USD", RiskDecisionRequest.Side.BUY, BigDecimal.ONE, new BigDecimal("60000.00"),
        OrderStatus.CREATED, now
    );
    when(orderService.create(any())).thenReturn(order);
    when(executionGateway.dispatch(orderId)).thenReturn(new OrderSubmissionResult(
        "auto-ord-1", "alpaca-paper-ord-99", OrderStatus.ACKNOWLEDGED, now, Map.of()
    ));

    // Run Cycle
    AutonomousExecutionResult result = orchestrator.runCycle(botId);

    // Verify Result & Causal Linkage
    assertThat(result.status()).isEqualTo("EXECUTED");
    assertThat(result.orderId()).isEqualTo(orderId);
    assertThat(result.decisionId()).isEqualTo(decisionId);

    verify(executionStore).saveIntent(any());
    verify(executionStore).saveExecution(any());
    verify(reconciliationService).reconcile(botId.toString());
    verify(audit).record(eq("AGENT"), eq(botId.toString()), eq("AUTONOMOUS_EXECUTION_COMPLETED"), eq("ORDER"), eq(orderId.toString()), any());
  }
}
