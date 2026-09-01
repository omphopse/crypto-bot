package io.algopilot.agent.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionExitEvent(
    UUID id,
    UUID positionId,
    UUID botId,
    String eventType,
    ExitReason exitReason,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal realizedPnl,
    UUID orderId,
    Instant eventTimestamp
) {}
