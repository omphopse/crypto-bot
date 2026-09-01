package io.algopilot.agent.context;

import java.time.Instant;

public record ReconciliationContext(
    String status,
    Instant lastMatchedAt,
    int criticalMismatchCount,
    boolean recoveryRequired,
    boolean isTradingBlocked
) {}
