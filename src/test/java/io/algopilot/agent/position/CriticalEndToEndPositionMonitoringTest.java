package io.algopilot.agent.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

class CriticalEndToEndPositionMonitoringTest {
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

    Bot bot = new Bot(botId, "Canary Bybit Demo", stratVersionId, Broker.BYBIT_DEMO, ExecutionMode.DEMO, BotStatus.RUNNING, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    Position position = new Position(positionId, botId.toString(), "BTCUSDT", new BigDecimal("1.00"), new BigDecimal("60000.00"), BigDecimal.ZERO, now);
    when(positionStore.findByBotId(botId.toString())).thenReturn(List.of(position));
  }

  @Test
  void testCriticalCausalChain_positionMonitoringToExitAndReconciliation() {
    // 1. Initial State: Position open at 60000.00, stop loss at 58800.00
    // Market moves down to 58500.00 (breaching stop)
    MarketContext market = new MarketContext("BTCUSDT", "BYBIT_DEMO", "DEMO", new BigDecimal("58500.00"), new BigDecimal("58490.00"), new BigDecimal("58510.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    TradingContext context = new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "BYBIT_DEMO", "DEMO",
        AgentState.MONITORING, AutonomousMode.DEMO_AUTONOMOUS, market,
        null, null, null, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );
    when(contextBuilder.buildContext(botId)).thenReturn(context);

    // 2. Mock Order Creation via Risk Engine & OrderService
    UUID exitOrderId = UUID.randomUUID();
    OrderRecord exitOrder = new OrderRecord(exitOrderId, "exit-98765", botId.toString(), stratVersionId.toString(), "BTCUSDT", RiskDecisionRequest.Side.SELL, new BigDecimal("1.00"), new BigDecimal("58500.00"), OrderStatus.CREATED, now);
    when(orderService.create(any())).thenReturn(exitOrder);

    // 3. Mock Dispatch via ExecutionGateway
    when(executionGateway.dispatch(exitOrderId)).thenReturn(new OrderSubmissionResult("exit-98765", "bybit-exit-001", OrderStatus.ACKNOWLEDGED, now, Map.of()));

    // 4. Run Monitoring Cycle
    List<PositionExitEvent> events = monitorService.monitorBotPositions(botId);

    // 5. Assert Causal Linkage
    assertThat(events).hasSize(1);
    PositionExitEvent event = events.get(0);
    assertThat(event.eventType()).isEqualTo("POSITION_EXIT_EXECUTED");
    assertThat(event.exitReason()).isEqualTo(ExitReason.HARD_STOP_LOSS);
    assertThat(event.orderId()).isEqualTo(exitOrderId);
    assertThat(event.price()).isEqualByComparingTo("58500.00");

    // Assert Lifecycle Transition to CLOSED
    PositionLifecycleRecord lifecycle = lifecycleStore.findLifecycleByPositionId(positionId).orElseThrow();
    assertThat(lifecycle.state()).isEqualTo(PositionLifecycleState.CLOSED);
    assertThat(lifecycle.currentQuantity()).isEqualByComparingTo("0");

    // Assert Snapshot Saved
    List<PositionSnapshot> snapshots = lifecycleStore.findSnapshotsByPositionId(positionId, 10);
    assertThat(snapshots).isNotEmpty();
    assertThat(snapshots.get(0).marketPrice()).isEqualByComparingTo("58500.00");

    // Assert Audit & Reconciliation
    verify(orderService, times(1)).create(any());
    verify(executionGateway, times(1)).dispatch(exitOrderId);
    verify(reconciliationService, times(1)).reconcile(botId.toString());
    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("POSITION_EXIT_EXECUTED"), eq("POSITION"), eq(positionId.toString()), any());
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
