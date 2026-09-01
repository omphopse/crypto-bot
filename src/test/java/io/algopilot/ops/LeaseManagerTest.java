package io.algopilot.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseManager;
import io.algopilot.ops.lease.LeaseStore;
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

class LeaseManagerTest {
  private MemoryLeaseStore store;
  private AuditEventWriter audit;
  private Clock clock;
  private LeaseManager leaseManager;
  private UUID botId;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    store = new MemoryLeaseStore();
    audit = mock(AuditEventWriter.class);
    leaseManager = new LeaseManager(store, audit, clock);
    botId = UUID.randomUUID();
  }

  @Test
  void testAcquireLease_firstInstance_succeeds() {
    Optional<BotRuntimeLease> lease = leaseManager.acquireLease(botId, "instance-A", 10000);

    assertThat(lease).isPresent();
    assertThat(lease.get().instanceId()).isEqualTo("instance-A");
    assertThat(leaseManager.isLeaseActive(botId, "instance-A")).isTrue();
  }

  @Test
  void testAcquireLease_secondInstanceCompeting_fails() {
    leaseManager.acquireLease(botId, "instance-A", 10000);

    Optional<BotRuntimeLease> leaseB = leaseManager.acquireLease(botId, "instance-B", 10000);
    assertThat(leaseB).isEmpty();
    assertThat(leaseManager.isLeaseActive(botId, "instance-B")).isFalse();
  }

  private static final class MemoryLeaseStore implements LeaseStore {
    private final Map<UUID, BotRuntimeLease> map = Collections.synchronizedMap(new HashMap<>());

    @Override
    public boolean tryAcquireLease(BotRuntimeLease lease) {
      BotRuntimeLease current = map.get(lease.botId());
      if (current == null || current.isExpired(lease.acquiredAt())) {
        map.put(lease.botId(), lease);
        return true;
      }
      return false;
    }

    @Override
    public boolean tryRenewLease(UUID botId, UUID leaseId, String instanceId, Instant newExpiresAt, Instant heartbeatAt) {
      BotRuntimeLease current = map.get(botId);
      if (current != null && current.leaseId().equals(leaseId) && current.instanceId().equals(instanceId)) {
        map.put(botId, new BotRuntimeLease(botId, instanceId, leaseId, current.acquiredAt(), newExpiresAt, heartbeatAt));
        return true;
      }
      return false;
    }

    @Override
    public void releaseLease(UUID botId, UUID leaseId) {
      map.remove(botId);
    }

    @Override
    public Optional<BotRuntimeLease> findLeaseByBotId(UUID botId) {
      return Optional.ofNullable(map.get(botId));
    }

    @Override
    public List<BotRuntimeLease> findAllActiveLeases(Instant now) {
      return map.values().stream().filter(l -> !l.isExpired(now)).toList();
    }
  }
}
