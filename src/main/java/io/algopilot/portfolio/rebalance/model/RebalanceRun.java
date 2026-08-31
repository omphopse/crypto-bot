package io.algopilot.portfolio.rebalance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RebalanceRun(
    UUID id,
    UUID planId,
    String status,
    BigDecimal maxDriftPct,
    int ordersCount,
    int executedCount,
    int failedCount,
    String details,
    Instant startedAt,
    Instant completedAt
) {}
