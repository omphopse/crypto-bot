package io.algopilot.reconciliation.broker;

import io.algopilot.order.OrderStatus;
import io.algopilot.risk.RiskDecisionRequest.Side;
import java.math.BigDecimal;
import java.time.Instant;

public record BrokerOrder(
    String brokerOrderId,
    String clientOrderId,
    String botId,
    String symbol,
    Side side,
    BigDecimal quantity,
    BigDecimal filledQuantity,
    BigDecimal price,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
