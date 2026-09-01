package io.algopilot.fill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FillStore {
  Optional<Fill> findByExchangeFillId(String exchangeFillId);
  Fill save(Fill fill);
  BigDecimal totalQuantityForOrder(UUID orderId);
  List<Fill> findByOrderId(UUID orderId);
  List<Fill> findByBotId(String botId);
  default List<Fill> findAll() { return List.of(); }
}
