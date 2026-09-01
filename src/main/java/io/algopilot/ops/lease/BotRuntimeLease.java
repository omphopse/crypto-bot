package io.algopilot.ops.lease;

import java.time.Instant;
import java.util.UUID;

public record BotRuntimeLease(
    UUID botId,
    String instanceId,
    UUID leaseId,
    Instant acquiredAt,
    Instant expiresAt,
    Instant heartbeatAt
) {
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }
}
