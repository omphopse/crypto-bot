package io.algopilot.ops.health;

import java.time.Instant;
import java.util.UUID;

public record HealthEvent(
    UUID id,
    ComponentType component,
    String instanceId,
    UUID botId,
    String eventType,
    String severity,
    String detail,
    Instant eventTimestamp
) {}
