package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.util.UUID;

public record WalkForwardRequest(
    UUID strategyVersionId,
    String symbol,
    String timeframe,
    int windowCount,
    int inSampleDays,
    int outOfSampleDays,
    BigDecimal initialCapital
) {}
