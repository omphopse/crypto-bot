package io.algopilot.ops.watchdog;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.ops.health.ComponentType;
import io.algopilot.ops.health.HealthEvent;
import io.algopilot.ops.health.HealthState;
import io.algopilot.ops.health.HeartbeatRecord;
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseStore;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchdogService {
  private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);

  private final HeartbeatStore heartbeatStore;
  private final LeaseStore leaseStore;
  private final BotStore botStore;
  private final OrderStore orderStore;
  private final ReconciliationService reconciliationService;
  private final AuditEventWriter audit;
  private final Clock clock;

  private final long botTimeoutMs;
  private final long orderStuckTimeoutMs;

  @org.springframework.beans.factory.annotation.Autowired
  public WatchdogService(
      HeartbeatStore heartbeatStore,
      LeaseStore leaseStore,
      BotStore botStore,
      OrderStore orderStore,
      ReconciliationService reconciliationService,
      AuditEventWriter audit,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock,
      @Value("${algopilot.watchdog.bot-timeout-ms:30000}") long botTimeoutMs,
      @Value("${algopilot.watchdog.order-stuck-timeout-ms:30000}") long orderStuckTimeoutMs
  ) {
    this.heartbeatStore = heartbeatStore;
    this.leaseStore = leaseStore;
    this.botStore = botStore;
    this.orderStore = orderStore;
    this.reconciliationService = reconciliationService;
    this.audit = audit;
    this.clock = clock;
    this.botTimeoutMs = botTimeoutMs;
    this.orderStuckTimeoutMs = orderStuckTimeoutMs;
  }

  public WatchdogService(
      HeartbeatStore heartbeatStore,
      LeaseStore leaseStore,
      BotStore botStore,
      OrderStore orderStore,
      ReconciliationService reconciliationService,
      AuditEventWriter audit,
      Clock clock
  ) {
    this(heartbeatStore, leaseStore, botStore, orderStore, reconciliationService, audit, clock, 30000L, 30000L);
  }

  @Transactional
  public List<WatchdogAlert> checkSystemHealth() {
    Instant now = clock.instant();
    List<WatchdogAlert> alerts = new ArrayList<>();

    List<Bot> activeBots = botStore.findAll().stream()
        .filter(b -> b.status() == BotStatus.RUNNING)
        .toList();

    for (Bot bot : activeBots) {
      // 1. Check Bot Heartbeats
      Optional<HeartbeatRecord> hbOpt = heartbeatStore.findHeartbeatsByBotId(bot.id()).stream().findFirst();
      if (hbOpt.isPresent() && hbOpt.get().isStale(now, botTimeoutMs)) {
        log.warn("WATCHDOG: Bot {} heartbeat is stale (age > {}ms). Pausing bot for safety.", bot.id(), botTimeoutMs);
        botStore.updateStatus(bot.id(), BotStatus.PAUSED);

        WatchdogAlert alert = new WatchdogAlert(
            UUID.randomUUID(), ComponentType.BOT_RUNTIME, bot.id(), "BOT_STALE",
            "Bot heartbeat timed out. Transitioned to PAUSED.", now
        );
        alerts.add(alert);

        heartbeatStore.recordHealthEvent(new HealthEvent(
            UUID.randomUUID(), ComponentType.BOT_RUNTIME, "watchdog", bot.id(),
            "BOT_STALE", "CRITICAL", alert.message(), now
        ));
        audit.record("SYSTEM", bot.id().toString(), "BOT_SAFETY_PAUSED", "BOT", bot.id().toString(),
            Map.of("reason", "HEARTBEAT_TIMEOUT"));
      }

      // 2. Check Lease Validity
      Optional<BotRuntimeLease> leaseOpt = leaseStore.findLeaseByBotId(bot.id());
      if (leaseOpt.isPresent() && leaseOpt.get().isExpired(now)) {
        log.warn("WATCHDOG: Runtime lease for bot {} expired. Pausing bot for multi-instance safety.", bot.id());
        botStore.updateStatus(bot.id(), BotStatus.PAUSED);

        WatchdogAlert alert = new WatchdogAlert(
            UUID.randomUUID(), ComponentType.APPLICATION, bot.id(), "RUNTIME_LEASE_LOST",
            "Runtime lease expired. Transitioned to PAUSED.", now
        );
        alerts.add(alert);

        heartbeatStore.recordHealthEvent(new HealthEvent(
            UUID.randomUUID(), ComponentType.APPLICATION, "watchdog", bot.id(),
            "RUNTIME_LEASE_LOST", "CRITICAL", alert.message(), now
        ));
        audit.record("SYSTEM", bot.id().toString(), "BOT_SAFETY_PAUSED", "BOT", bot.id().toString(),
            Map.of("reason", "RUNTIME_LEASE_LOST"));
      }

      // 3. Check Stuck In-Flight Orders
      List<OrderRecord> openOrders = orderStore.findOpenOrdersByBotId(bot.id().toString());
      for (OrderRecord ord : openOrders) {
        if ((ord.status() == OrderStatus.CREATED || ord.status() == OrderStatus.SUBMITTED) &&
            now.toEpochMilli() - ord.createdAt().toEpochMilli() > orderStuckTimeoutMs) {
          log.error("WATCHDOG: Order {} for bot {} is stuck in {} for > {}ms. Pausing and triggering reconciliation.",
              ord.id(), bot.id(), ord.status(), orderStuckTimeoutMs);

          botStore.updateStatus(bot.id(), BotStatus.PAUSED);

          WatchdogAlert alert = new WatchdogAlert(
              UUID.randomUUID(), ComponentType.EXECUTION_PIPELINE, bot.id(), "ORDER_STUCK",
              "Order " + ord.id() + " stuck in " + ord.status() + ". Pausing bot and triggering reconciliation.", now
          );
          alerts.add(alert);

          heartbeatStore.recordHealthEvent(new HealthEvent(
              UUID.randomUUID(), ComponentType.EXECUTION_PIPELINE, "watchdog", bot.id(),
              "ORDER_STUCK", "CRITICAL", alert.message(), now
          ));
          audit.record("SYSTEM", bot.id().toString(), "ORDER_STUCK", "ORDER", ord.id().toString(),
              Map.of("status", ord.status().name(), "ageMs", String.valueOf(now.toEpochMilli() - ord.createdAt().toEpochMilli())));

          // Run reconciliation
          try {
            if (reconciliationService != null) {
              reconciliationService.reconcile(bot.id().toString());
            }
          } catch (Exception e) {
            log.error("Reconciliation failed after stuck order detected for bot {}: {}", bot.id(), e.getMessage());
          }
        }
      }
    }

    return alerts;
  }
}
