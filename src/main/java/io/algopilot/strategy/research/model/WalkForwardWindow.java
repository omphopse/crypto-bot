package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalkForwardWindow(
    UUID id,
    UUID experimentId,
    int windowIndex,
    Instant inSampleStart,
    Instant inSampleEnd,
    Instant outOfSampleStart,
    Instant outOfSampleEnd,
    BigDecimal inSampleNetReturnPct,
    BigDecimal outOfSampleNetReturnPct,
    BigDecimal oosDegradationRatio
) {}
