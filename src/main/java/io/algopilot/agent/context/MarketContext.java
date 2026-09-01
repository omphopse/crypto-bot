package io.algopilot.agent.context;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketContext(
    String symbol,
    String provider,
    String environment,
    BigDecimal lastPrice,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal spread,
    BigDecimal volume,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    String timeframe,
    Instant marketTimestamp,
    Instant receivedAt,
    long freshnessMs,
    FreshnessStatus freshnessStatus,
    String validationStatus
) {}
