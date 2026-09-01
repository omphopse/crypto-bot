package io.algopilot.agent.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionLifecycleRecord(
    UUID positionId,
    UUID botId,
    UUID strategyVersionId,
    String symbol,
    String side,
    BigDecimal initialQuantity,
    BigDecimal currentQuantity,
    BigDecimal entryPrice,
    BigDecimal initialStopLoss,
    BigDecimal currentStopLoss,
    BigDecimal takeProfit,
    BigDecimal trailingStopPct,
    BigDecimal highWaterMark,
    PositionLifecycleState state,
    Instant openedAt,
    Instant closedAt,
    Instant updatedAt
) {
  public boolean isOpen() {
    return state != PositionLifecycleState.CLOSED && currentQuantity.compareTo(BigDecimal.ZERO) > 0;
  }
}
