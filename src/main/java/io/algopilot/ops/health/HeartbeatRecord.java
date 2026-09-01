package io.algopilot.ops.health;

import java.time.Instant;
import java.util.UUID;

public record HeartbeatRecord(
    UUID id,
    ComponentType component,
    String instanceId,
    UUID botId,
    long sequenceNumber,
    HealthState status,
    Instant timestamp,
    String metadataJson
) {
  public boolean isStale(Instant now, long timeoutMs) {
    return now.toEpochMilli() - timestamp.toEpochMilli() > timeoutMs;
  }
}
