package io.algopilot.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderStore {
  Optional<OrderRecord> findByClientOrderId(String clientOrderId);
  Optional<OrderRecord> findById(UUID id);
  List<OrderRecord> findByBotId(String botId);
  List<OrderRecord> findOpenOrdersByBotId(String botId);
  default List<OrderRecord> findAll(int limit) { return List.of(); }
  OrderRecord save(OrderRecord order);
  OrderRecord updateStatus(UUID id, OrderStatus status);
}
