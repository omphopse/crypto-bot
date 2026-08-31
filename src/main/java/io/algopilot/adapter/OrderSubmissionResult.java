package io.algopilot.adapter;

import io.algopilot.order.OrderStatus;
import java.time.Instant;
import java.util.Map;

public record OrderSubmissionResult(
    String clientOrderId,
    String exchangeOrderId,
    OrderStatus status,
    Instant submittedAt,
    Map<String, Object> details
) {}
