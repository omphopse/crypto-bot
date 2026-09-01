package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExperimentTrade(
    UUID id,
    UUID experimentId,
    String symbol,
    String side,
    Instant entryTime,
    Instant exitTime,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal grossPnl,
    BigDecimal netPnl,
    BigDecimal fee,
    BigDecimal slippage,
    BigDecimal spreadCost,
    String exitReason
) {}
