package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.algopilot.adapter.ExecutionGateway;
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
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.ops.health.ComponentType;
import io.algopilot.ops.health.HealthEvent;
import io.algopilot.ops.health.HealthState;
import io.algopilot.ops.health.HeartbeatRecord;
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.lease.LeaseStore;
import io.algopilot.ops.recovery.RecoveryService;
import io.algopilot.ops.watchdog.WatchdogAlert;
import io.algopilot.ops.watchdog.WatchdogService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderService;
import io.algopilot.order.OrderStore;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosFaultInjectionTest {
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
  }

  @Test
  void testChaos_staleMarketData_blocksNewEntries() {
    // When market data is stale, safety checks or strategy validator reject intent
    TradingContext context = createMockContext(true, false);
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    StructuredTradeDecision decision = new StructuredTradeDecision(
        UUID.randomUUID(), context.contextId(), "hash", botId, UUID.randomUUID(), UUID.randomUUID(),
        "fake", "mock", TradeAction.BUY, "BTC/USD", "FLAT", new BigDecimal("0.8"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "INTRADAY", "thesis",
        List.of(), List.of(), List.of(), ValidationStatus.VALIDATED, null, 100L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );
    when(decisionEngine.analyze(context)).thenReturn(decision);
    when(strategyValidator.validate(any(), any())).thenReturn(new StrategyValidationResult(UUID.randomUUID(), UUID.randomUUID(), decision.id(), false, "MARKET_DATA_STALE", now));

    AutonomousExecutionResult result = orchestrator.runCycle(botId);

    assertThat(result.status()).isEqualTo("REJECTED_STRATEGY");
    assertThat(result.detail()).isEqualTo("MARKET_DATA_STALE");
    verify(orderService, never()).create(any());
  }

  @Test
  void testChaos_brokerDispatchFailure_failsSafeAndDoesNotRepeatOrder() {
    TradingContext context = createMockContext(false, false);
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    StructuredTradeDecision decision = new StructuredTradeDecision(
        UUID.randomUUID(), context.contextId(), "hash", botId, UUID.randomUUID(), UUID.randomUUID(),
        "fake", "mock", TradeAction.BUY, "BTC/USD", "FLAT", new BigDecimal("0.8"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "INTRADAY", "thesis",
        List.of(), List.of(), List.of(), ValidationStatus.VALIDATED, null, 100L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );
    when(decisionEngine.analyze(context)).thenReturn(decision);
    when(strategyValidator.validate(any(), any())).thenReturn(new StrategyValidationResult(UUID.randomUUID(), UUID.randomUUID(), decision.id(), true, "VALID", now));

    OrderRecord order = new OrderRecord(
        UUID.randomUUID(), "ord-1", botId.toString(), UUID.randomUUID().toString(),
        "BTC/USD", RiskDecisionRequest.Side.BUY, BigDecimal.ONE, new BigDecimal("60000.00"),
        OrderStatus.CREATED, now
    );
    when(orderService.create(any())).thenReturn(order);
    when(executionGateway.dispatch(order.id())).thenThrow(new RuntimeException("BROKER_CONNECTION_TIMEOUT"));

    AutonomousExecutionResult result = orchestrator.runCycle(botId);

    assertThat(result.status()).isEqualTo("FAILED_BROKER");
    assertThat(result.detail()).contains("BROKER_CONNECTION_TIMEOUT");
    // Verify no secondary order was placed
    verify(orderService, times(1)).create(any());
  }

  private TradingContext createMockContext(boolean isStale, boolean emergencyStop) {
    FreshnessStatus fStatus = isStale ? FreshnessStatus.STALE : FreshnessStatus.FRESH;
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, isStale ? 120000L : 1000L, fStatus, "VALID");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), emergencyStop, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    SafetySummary safety = new SafetySummary(!emergencyStop, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(isStale ? 120000L : 1000L, 0L, 0L, fStatus);

    return new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, null, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );
  }
}
