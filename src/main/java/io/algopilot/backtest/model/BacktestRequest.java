package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestRequest(
    UUID strategyVersionId,
    String symbol,
    String timeframe,
    Instant startTime,
    Instant endTime,
    BigDecimal initialCapital,
    Integer slippageBps,
    Integer feeBps
) {}
