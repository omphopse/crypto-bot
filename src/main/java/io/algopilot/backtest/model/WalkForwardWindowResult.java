package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record WalkForwardWindowResult(
    int windowIndex,
    Instant inSampleStart,
    Instant inSampleEnd,
    Instant outOfSampleStart,
    Instant outOfSampleEnd,
    BigDecimal inSampleSharpe,
    BigDecimal outOfSampleSharpe,
    BigDecimal outOfSampleEfficiency,
    Map<String, Object> parameters
) {}
