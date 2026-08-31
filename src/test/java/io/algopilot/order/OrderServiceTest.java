package io.algopilot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskEngine;
import io.algopilot.risk.RiskDecisionService;
import io.algopilot.risk.RiskDecisionStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
  @Test void creates_once_then_returns_the_original_order_on_retry() {
    OrderStore store = new MemoryStore(); AuditEventWriter audit = mock(AuditEventWriter.class);
    OrderService service = new OrderService(riskService(), store, audit);
    OrderRecord first = service.create(command(false));
    OrderRecord retry = service.create(command(false));
    assertThat(retry.id()).isEqualTo(first.id()); verify(audit, times(1)).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
  }
  @Test void rejected_risk_decision_never_creates_an_order() {
    MemoryStore store = new MemoryStore(); OrderService service = new OrderService(riskService(), store, mock(AuditEventWriter.class));
    assertThatThrownBy(() -> service.create(command(true))).isInstanceOf(OrderRejectedException.class);
    assertThat(store.orders).isEmpty();
  }
  private RiskDecisionRequest command(boolean stopped) { return new RiskDecisionRequest("client-1", "bot-1", "v1", "BTC/USD", RiskDecisionRequest.Side.BUY, BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), false, stopped, false, 0, 0, 0); }
  private RiskDecisionService riskService() { return new RiskDecisionService(new RiskEngine(), decision -> decision, mock(AuditEventWriter.class), new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()); }
  private static final class MemoryStore implements OrderStore {
    final HashMap<String, OrderRecord> orders = new HashMap<>();
    public Optional<OrderRecord> findByClientOrderId(String id) { return Optional.ofNullable(orders.get(id)); }
    public Optional<OrderRecord> findById(java.util.UUID id) { return orders.values().stream().filter(order -> order.id().equals(id)).findFirst(); }
    public List<OrderRecord> findByBotId(String botId) { return orders.values().stream().filter(o -> o.botId().equals(botId)).toList(); }
    public List<OrderRecord> findOpenOrdersByBotId(String botId) { return orders.values().stream().filter(o -> o.botId().equals(botId)).toList(); }
    public OrderRecord save(OrderRecord order) { orders.put(order.clientOrderId(), order); return order; }
    public OrderRecord updateStatus(java.util.UUID id, OrderStatus status) { OrderRecord order = findById(id).orElseThrow(); OrderRecord changed = new OrderRecord(order.id(), order.clientOrderId(), order.botId(), order.strategyVersionId(), order.symbol(), order.side(), order.quantity(), order.referencePrice(), status, order.createdAt()); orders.put(changed.clientOrderId(), changed); return changed; }
  }
}
