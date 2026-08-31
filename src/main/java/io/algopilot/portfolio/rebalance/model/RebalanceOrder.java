package io.algopilot.portfolio.rebalance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RebalanceOrder(
    UUID id,
    UUID rebalanceRunId,
    UUID botId,
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal price,
    UUID orderId,
    String status,
    Instant createdAt
) {}
