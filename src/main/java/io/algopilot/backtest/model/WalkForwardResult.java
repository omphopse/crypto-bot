package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WalkForwardResult(
    UUID id,
    UUID strategyVersionId,
    String symbol,
    String timeframe,
    int windowCount,
    BigDecimal avgOosEfficiency,
    List<WalkForwardWindowResult> windows,
    Instant createdAt
) {}
