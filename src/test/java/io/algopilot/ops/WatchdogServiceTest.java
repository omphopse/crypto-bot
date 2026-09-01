package io.algopilot.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseStore;
import io.algopilot.ops.watchdog.WatchdogAlert;
import io.algopilot.ops.watchdog.WatchdogService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
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

class WatchdogServiceTest {
  private MemoryHeartbeatStore heartbeatStore;
  private LeaseStore leaseStore;
  private BotStore botStore;
  private OrderStore orderStore;
  private ReconciliationService reconciliationService;
  private AuditEventWriter audit;
  private Clock clock;
  private WatchdogService watchdog;

  private UUID botId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    heartbeatStore = new MemoryHeartbeatStore();
    leaseStore = mock(LeaseStore.class);
    botStore = mock(BotStore.class);
    orderStore = mock(OrderStore.class);
    reconciliationService = mock(ReconciliationService.class);
    audit = mock(AuditEventWriter.class);

    watchdog = new WatchdogService(
        heartbeatStore, leaseStore, botStore, orderStore, reconciliationService, audit, clock, 30000L, 30000L
    );

    botId = UUID.randomUUID();
    Bot runningBot = new Bot(botId, "Canary Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findAll()).thenReturn(List.of(runningBot));
  }

  @Test
  void testWatchdog_whenHeartbeatStale_pausesBotAndAlerts() {
    // Heartbeat from 45s ago (> 30s timeout)
    Instant oldTimestamp = now.minusSeconds(45);
    heartbeatStore.recordHeartbeat(new HeartbeatRecord(
        UUID.randomUUID(), ComponentType.BOT_RUNTIME, "instance-1", botId, 1L, HealthState.HEALTHY, oldTimestamp, null
    ));

    List<WatchdogAlert> alerts = watchdog.checkSystemHealth();

    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).alertType()).isEqualTo("BOT_STALE");
    verify(botStore, times(1)).updateStatus(botId, BotStatus.PAUSED);
  }

  @Test
  void testWatchdog_whenLeaseExpired_pausesBot() {
    // Valid heartbeat but expired lease
    heartbeatStore.recordHeartbeat(new HeartbeatRecord(
        UUID.randomUUID(), ComponentType.BOT_RUNTIME, "instance-1", botId, 1L, HealthState.HEALTHY, now.minusSeconds(5), null
    ));

    BotRuntimeLease expiredLease = new BotRuntimeLease(
        botId, "instance-1", UUID.randomUUID(), now.minusSeconds(60), now.minusSeconds(10), now.minusSeconds(20)
    );
    when(leaseStore.findLeaseByBotId(botId)).thenReturn(Optional.of(expiredLease));

    List<WatchdogAlert> alerts = watchdog.checkSystemHealth();

    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).alertType()).isEqualTo("RUNTIME_LEASE_LOST");
    verify(botStore, times(1)).updateStatus(botId, BotStatus.PAUSED);
  }

  @Test
  void testWatchdog_whenOrderStuck_pausesBotAndReconciles() {
    // Valid heartbeat and valid lease
    heartbeatStore.recordHeartbeat(new HeartbeatRecord(
        UUID.randomUUID(), ComponentType.BOT_RUNTIME, "instance-1", botId, 1L, HealthState.HEALTHY, now.minusSeconds(5), null
    ));
    when(leaseStore.findLeaseByBotId(botId)).thenReturn(Optional.empty());

    // Stuck Order (created 40s ago > 30s timeout)
    OrderRecord stuckOrder = new OrderRecord(
        UUID.randomUUID(), "ord-123", botId.toString(), UUID.randomUUID().toString(),
        "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.00"), new BigDecimal("60000.00"),
        OrderStatus.SUBMITTED, now.minusSeconds(40)
    );
    when(orderStore.findOpenOrdersByBotId(botId.toString())).thenReturn(List.of(stuckOrder));

    List<WatchdogAlert> alerts = watchdog.checkSystemHealth();

    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).alertType()).isEqualTo("ORDER_STUCK");
    verify(botStore, times(1)).updateStatus(botId, BotStatus.PAUSED);
    verify(reconciliationService, times(1)).reconcile(botId.toString());
  }

  private static final class MemoryHeartbeatStore implements HeartbeatStore {
    private final Map<String, HeartbeatRecord> map = Collections.synchronizedMap(new HashMap<>());
    private final List<HealthEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override public HeartbeatRecord recordHeartbeat(HeartbeatRecord r) { map.put(r.component() + "_" + r.instanceId() + "_" + r.botId(), r); return r; }
    @Override public Optional<HeartbeatRecord> findLatestHeartbeat(ComponentType c, String i, UUID b) { return Optional.ofNullable(map.get(c + "_" + i + "_" + b)); }
    @Override public List<HeartbeatRecord> findAllActiveHeartbeats() { return new ArrayList<>(map.values()); }
    @Override public List<HeartbeatRecord> findHeartbeatsByBotId(UUID b) { return map.values().stream().filter(r -> b.equals(r.botId())).toList(); }
    @Override public HealthEvent recordHealthEvent(HealthEvent e) { events.add(e); return e; }
    @Override public List<HealthEvent> findRecentHealthEvents(int limit) { return events.stream().limit(limit).toList(); }
  }
}
