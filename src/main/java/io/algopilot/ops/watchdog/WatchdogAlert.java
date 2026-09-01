package io.algopilot.ops.watchdog;

import io.algopilot.ops.health.ComponentType;
import java.time.Instant;
import java.util.UUID;

public record WatchdogAlert(
    UUID alertId,
    ComponentType component,
    UUID botId,
    String alertType,
    String message,
    Instant timestamp
) {}
