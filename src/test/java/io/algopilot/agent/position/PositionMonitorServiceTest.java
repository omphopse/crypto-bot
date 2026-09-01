package io.algopilot.agent.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.execution.PositionDecisionService;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderService;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionMonitorServiceTest {
  private PositionStore positionStore;
  private MemoryPositionLifecycleStore lifecycleStore;
  private ContextBuilderService contextBuilder;
  private ExitConditionEvaluator exitEvaluator;
  private TrailingStopManager trailingStopManager;
  private PositionDecisionService positionDecisionService;
  private OrderService orderService;
  private ExecutionGateway executionGateway;
  private ReconciliationService reconciliationService;
  private BotStore botStore;
  private AuditEventWriter audit;
  private Clock clock;
  private PositionMonitorService monitorService;

  private UUID botId;
  private UUID positionId;
  private UUID stratVersionId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    positionStore = mock(PositionStore.class);
    lifecycleStore = new MemoryPositionLifecycleStore();
    contextBuilder = mock(ContextBuilderService.class);
    exitEvaluator = new ExitConditionEvaluator(new StopLossManager(), new TakeProfitManager(), new TrailingStopManager());
    trailingStopManager = new TrailingStopManager();
    positionDecisionService = mock(PositionDecisionService.class);
    orderService = mock(OrderService.class);
    executionGateway = mock(ExecutionGateway.class);
    reconciliationService = mock(ReconciliationService.class);
    botStore = mock(BotStore.class);
    audit = mock(AuditEventWriter.class);

    monitorService = new PositionMonitorService(
        positionStore, lifecycleStore, contextBuilder, exitEvaluator, trailingStopManager,
        positionDecisionService, orderService, executionGateway, reconciliationService,
        botStore, audit, clock
    );

    botId = UUID.randomUUID();
    positionId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();

    Bot bot = new Bot(botId, "Canary Bot", stratVersionId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    Position position = new Position(positionId, botId.toString(), "BTC/USD", new BigDecimal("1.00"), new BigDecimal("60000.00"), BigDecimal.ZERO, now);
    when(positionStore.findByBotId(botId.toString())).thenReturn(List.of(position));
  }

  @Test
  void testMonitorBotPositions_whenHardStopHit_executesExitOrderAndReconciles() {
    // Current price 57000.00 drops below stop loss of 58800.00 (98% of entry 60000)
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("57000.00"), new BigDecimal("56990.00"), new BigDecimal("57010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    TradingContext context = new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.MONITORING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, null, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    UUID exitOrderId = UUID.randomUUID();
    OrderRecord exitOrder = new OrderRecord(exitOrderId, "exit-12345", botId.toString(), stratVersionId.toString(), "BTC/USD", RiskDecisionRequest.Side.SELL, new BigDecimal("1.00"), new BigDecimal("57000.00"), OrderStatus.CREATED, now);
    when(orderService.create(any())).thenReturn(exitOrder);
    when(executionGateway.dispatch(exitOrderId)).thenReturn(new OrderSubmissionResult("exit-12345", "alpaca-exit-999", OrderStatus.ACKNOWLEDGED, now, Map.of()));

    List<PositionExitEvent> events = monitorService.monitorBotPositions(botId);

    assertThat(events).hasSize(1);
    PositionExitEvent event = events.get(0);
    assertThat(event.eventType()).isEqualTo("POSITION_EXIT_EXECUTED");
    assertThat(event.exitReason()).isEqualTo(ExitReason.HARD_STOP_LOSS);
    assertThat(event.orderId()).isEqualTo(exitOrderId);

    verify(orderService, times(1)).create(any());
    verify(executionGateway, times(1)).dispatch(exitOrderId);
    verify(reconciliationService, times(1)).reconcile(botId.toString());
  }

  @Test
  void testMonitorBotPositions_whenObserveOnly_recordsExitEventWithoutOrders() {
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("57000.00"), new BigDecimal("56990.00"), new BigDecimal("57010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    // MODE = OBSERVE_ONLY
    TradingContext context = new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.MONITORING, AutonomousMode.OBSERVE_ONLY, market,
        null, null, null, null, List.of(), List.of(), null, null,
        List.of(), null, freshness, safety
    );
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    List<PositionExitEvent> events = monitorService.monitorBotPositions(botId);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventType()).isEqualTo("POSITION_EXIT_OBSERVE_ONLY");
    assertThat(events.get(0).orderId()).isNull();

    // Verify zero order calls!
    verify(orderService, never()).create(any());
    verify(executionGateway, never()).dispatch(any());
  }

  @Test
  void testMonitorBotPositions_whenAiProposesHold_hardStopOverridesAndClosesPosition() {
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("57000.00"), new BigDecimal("56990.00"), new BigDecimal("57010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    TradingContext context = new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.MONITORING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, null, null, List.of(), List.of(), null, null,
        List.of(), null, freshness, safety
    );
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    // AI claims HOLD
    when(positionDecisionService.evaluateExit(any(), any())).thenReturn(Optional.of(TradeAction.HOLD));

    UUID exitOrderId = UUID.randomUUID();
    OrderRecord exitOrder = new OrderRecord(exitOrderId, "exit-12345", botId.toString(), stratVersionId.toString(), "BTC/USD", RiskDecisionRequest.Side.SELL, new BigDecimal("1.00"), new BigDecimal("57000.00"), OrderStatus.CREATED, now);
    when(orderService.create(any())).thenReturn(exitOrder);
    when(executionGateway.dispatch(exitOrderId)).thenReturn(new OrderSubmissionResult("exit-12345", "alpaca-exit-999", OrderStatus.ACKNOWLEDGED, now, Map.of()));

    List<PositionExitEvent> events = monitorService.monitorBotPositions(botId);

    // Hard Stop Loss MUST execute regardless of AI claiming HOLD!
    assertThat(events).hasSize(1);
    assertThat(events.get(0).exitReason()).isEqualTo(ExitReason.HARD_STOP_LOSS);
    verify(orderService, times(1)).create(any());
  }

  private static final class MemoryPositionLifecycleStore implements PositionLifecycleStore {
    private final Map<UUID, PositionLifecycleRecord> lifecycles = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, PositionSnapshot> snapshots = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, PositionStopRecord> stops = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, PositionExitEvent> exits = Collections.synchronizedMap(new HashMap<>());

    @Override public PositionLifecycleRecord saveLifecycle(PositionLifecycleRecord r) { lifecycles.put(r.positionId(), r); return r; }
    @Override public Optional<PositionLifecycleRecord> findLifecycleByPositionId(UUID id) { return Optional.ofNullable(lifecycles.get(id)); }
    @Override public List<PositionLifecycleRecord> findOpenLifecyclesByBotId(UUID botId) { return lifecycles.values().stream().filter(r -> r.botId().equals(botId) && r.isOpen()).toList(); }
    @Override public PositionSnapshot saveSnapshot(PositionSnapshot s) { snapshots.put(s.id(), s); return s; }
    @Override public List<PositionSnapshot> findSnapshotsByPositionId(UUID id, int limit) { return snapshots.values().stream().filter(s -> s.positionId().equals(id)).limit(limit).toList(); }
    @Override public PositionStopRecord saveStopRecord(PositionStopRecord r) { stops.put(r.id(), r); return r; }
    @Override public List<PositionStopRecord> findStopHistoryByPositionId(UUID id) { return stops.values().stream().filter(s -> s.positionId().equals(id)).toList(); }
    @Override public PositionExitEvent saveExitEvent(PositionExitEvent e) { exits.put(e.id(), e); return e; }
    @Override public List<PositionExitEvent> findExitEventsByPositionId(UUID id) { return exits.values().stream().filter(e -> e.positionId().equals(id)).toList(); }
  }
}
