package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StrategyHealthMetric(
    UUID id,
    UUID strategyId,
    BigDecimal backtestExpectancy,
    BigDecimal paperExpectancy,
    BigDecimal demoExpectancy,
    BigDecimal driftRatio,
    String healthStatus,
    Instant evaluatedAt
) {}
