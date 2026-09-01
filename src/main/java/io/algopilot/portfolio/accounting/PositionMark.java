package io.algopilot.portfolio.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Marked-to-market position record reflecting current verified market price,
 * cost basis, position market value, unrealized P&L, and realized P&L.
 */
public record PositionMark(
    UUID id,
    String botId,
    String symbol,
    BigDecimal quantity,
    BigDecimal averageEntryPrice,
    BigDecimal currentMarketPrice,
    BigDecimal costBasis,
    BigDecimal marketValue,
    BigDecimal unrealizedPnl,
    BigDecimal realizedPnl,
    Instant updatedAt
) {}
