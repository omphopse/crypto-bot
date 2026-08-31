package io.algopilot.event;

import java.time.Instant;

public record SystemEvent(
    String topic,
    String eventType,
    Instant timestamp,
    Object payload
) {}
