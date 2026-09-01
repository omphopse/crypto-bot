package io.algopilot.agent.context;

import java.math.BigDecimal;
import java.time.Instant;

public record IndicatorContext(
    String symbol,
    String timeframe,
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
    boolean isWarmedUp,
    Instant calculatedAt
) {}
