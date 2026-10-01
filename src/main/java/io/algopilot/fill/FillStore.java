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
  default List<Fill> findByBotIdAndBrokerAccountId(String botId, String brokerAccountId) {
    if (brokerAccountId == null) return findByBotId(botId);
    return findByBotId(botId).stream()
        .filter(f -> f.brokerAccountId() == null || f.brokerAccountId().equals(brokerAccountId))
        .toList();
  }
  default List<Fill> findAll() { return List.of(); }
}
