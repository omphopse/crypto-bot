package io.algopilot.agent.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionSnapshot(
    UUID id,
    UUID positionId,
    UUID botId,
    String symbol,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal marketPrice,
    BigDecimal marketValue,
    BigDecimal unrealizedPnl,
    BigDecimal realizedPnl,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    BigDecimal trailingStop,
    BigDecimal mfe,
    BigDecimal mae,
    long holdingTimeMs,
    Instant snapshotTimestamp
) {}
