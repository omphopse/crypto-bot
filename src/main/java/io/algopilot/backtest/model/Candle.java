package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
    String symbol,
    String timeframe,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume,
    Instant timestamp
) {}
