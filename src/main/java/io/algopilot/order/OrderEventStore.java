package io.algopilot.order;

public interface OrderEventStore { OrderEvent append(OrderEvent event); }
