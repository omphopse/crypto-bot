package io.algopilot.agent.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OpenOrderContext(
    UUID orderId,
    String clientOrderId,
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal price,
    String status,
    BigDecimal reservedExposure,
    Instant createdAt
) {}
