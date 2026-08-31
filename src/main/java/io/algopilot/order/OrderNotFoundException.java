package io.algopilot.order;

import java.util.UUID;
public class OrderNotFoundException extends RuntimeException { public OrderNotFoundException(UUID id) { super("ORDER_NOT_FOUND:" + id); } }
