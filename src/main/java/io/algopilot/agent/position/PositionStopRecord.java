package io.algopilot.agent.position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionStopRecord(
    UUID id,
    UUID positionId,
    UUID botId,
    BigDecimal previousStop,
    BigDecimal newStop,
    String reason,
    UUID decisionId,
    Instant modifiedAt
) {}
