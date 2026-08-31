package io.algopilot.adapter;

import io.algopilot.order.OrderStatus;
import java.time.Instant;

public record OrderCancellationResult(
    String clientOrderId,
    String exchangeOrderId,
    OrderStatus status,
    Instant cancelledAt,
    String message
) {}
