package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.util.UUID;

public record RegimeResult(
    UUID id,
    UUID experimentId,
    String regime,
    int tradeCount,
    BigDecimal netPnl,
    BigDecimal winRatePct,
    BigDecimal profitFactor,
    BigDecimal netExpectancy,
    BigDecimal maxDrawdownPct
) {}
