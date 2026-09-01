package io.algopilot.ops.lease;

import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeaseManager {
  private static final Logger log = LoggerFactory.getLogger(LeaseManager.class);

  private final LeaseStore leaseStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  public LeaseManager(LeaseStore leaseStore, AuditEventWriter audit, Clock clock) {
    this.leaseStore = leaseStore;
    this.audit = audit;
    this.clock = clock;
  }

  public Optional<BotRuntimeLease> acquireLease(UUID botId, String instanceId, long durationMs) {
    Instant now = clock.instant();
    Instant expiresAt = now.plusMillis(durationMs);
    UUID leaseId = UUID.randomUUID();

    BotRuntimeLease lease = new BotRuntimeLease(botId, instanceId, leaseId, now, expiresAt, now);
    boolean acquired = leaseStore.tryAcquireLease(lease);

    if (acquired) {
      log.info("LEASE_ACQUIRED for botId={} instanceId={} leaseId={}", botId, instanceId, leaseId);
      audit.record("SYSTEM", botId.toString(), "LEASE_ACQUIRED", "BOT_LEASE", leaseId.toString(),
          Map.of("instanceId", instanceId, "expiresAt", expiresAt.toString()));
      return Optional.of(lease);
    } else {
      log.warn("LEASE_ACQUISITION_FAILED for botId={} instanceId={} - active lease held elsewhere", botId, instanceId);
      return Optional.empty();
    }
  }

  public boolean renewLease(UUID botId, UUID leaseId, String instanceId, long durationMs) {
    Instant now = clock.instant();
    Instant newExpiresAt = now.plusMillis(durationMs);
    boolean renewed = leaseStore.tryRenewLease(botId, leaseId, instanceId, newExpiresAt, now);
    if (!renewed) {
      log.warn("LEASE_RENEWAL_FAILED for botId={} leaseId={} instanceId={}", botId, leaseId, instanceId);
      audit.record("SYSTEM", botId.toString(), "LEASE_LOST", "BOT_LEASE", leaseId.toString(),
          Map.of("instanceId", instanceId, "reason", "RENEWAL_REJECTED"));
    }
    return renewed;
  }

  public void releaseLease(UUID botId, UUID leaseId) {
    leaseStore.releaseLease(botId, leaseId);
    log.info("LEASE_RELEASED for botId={} leaseId={}", botId, leaseId);
    audit.record("SYSTEM", botId.toString(), "LEASE_RELEASED", "BOT_LEASE", leaseId.toString(), Map.of());
  }

  public boolean isLeaseActive(UUID botId, String instanceId) {
    Instant now = clock.instant();
    Optional<BotRuntimeLease> leaseOpt = leaseStore.findLeaseByBotId(botId);
    return leaseOpt.isPresent() &&
        leaseOpt.get().instanceId().equals(instanceId) &&
        !leaseOpt.get().isExpired(now);
  }
}
