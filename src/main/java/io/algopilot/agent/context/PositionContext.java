package io.algopilot.agent.context;

import java.math.BigDecimal;

public record PositionContext(
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal averageEntryPrice,
    BigDecimal currentMarketPrice,
    BigDecimal marketValue,
    BigDecimal costBasis,
    BigDecimal unrealizedPnl,
    BigDecimal realizedPnl
) {}
