package io.algopilot.ops.lease;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseStore {
  boolean tryAcquireLease(BotRuntimeLease lease);
  boolean tryRenewLease(UUID botId, UUID leaseId, String instanceId, java.time.Instant newExpiresAt, java.time.Instant heartbeatAt);
  void releaseLease(UUID botId, UUID leaseId);
  Optional<BotRuntimeLease> findLeaseByBotId(UUID botId);
  List<BotRuntimeLease> findAllActiveLeases(java.time.Instant now);
}
