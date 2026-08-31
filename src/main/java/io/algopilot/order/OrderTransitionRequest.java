package io.algopilot.order;

import jakarta.validation.constraints.NotNull;
public record OrderTransitionRequest(@NotNull OrderStatus targetStatus, String exchangeOrderId, String detail) {}
