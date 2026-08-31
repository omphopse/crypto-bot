package io.algopilot.order;

import java.time.Instant;
import java.util.UUID;

public record OrderEvent(UUID id, UUID orderId, OrderStatus previousStatus, OrderStatus nextStatus,
                         String exchangeOrderId, String detail, Instant occurredAt) {}
