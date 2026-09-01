package io.algopilot.market.scanner;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deterministic quantitative indicator state evaluated for scanner detection.
 * Only valid if isWarmedUp is true.
 */
public record IndicatorSnapshot(
    String symbol,
    String timeframe,
    Instant timestamp,
    BigDecimal emaFast,
    BigDecimal emaSlow,
    BigDecimal sma50,
    BigDecimal sma200,
    BigDecimal rsi14,
    BigDecimal macd,
    BigDecimal macdSignal,
    BigDecimal macdHistogram,
    BigDecimal atr14,
    BigDecimal bbUpper,
    BigDecimal bbMiddle,
    BigDecimal bbLower,
    BigDecimal averageVolume,
    BigDecimal currentVolume,
    BigDecimal priceChangePct,
    BigDecimal volatility,
    boolean isWarmedUp
) {}
