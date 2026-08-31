package io.algopilot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLifecycleServiceTest {
  @Test void advances_valid_lifecycle_transition() {
    MemoryStore store = new MemoryStore(order(OrderStatus.CREATED)); MemoryEvents events = new MemoryEvents(); OrderLifecycleService service = new OrderLifecycleService(store, events, mock(AuditEventWriter.class));
    OrderRecord result = service.transition(store.value.id(), new OrderTransitionRequest(OrderStatus.SUBMITTED, "exchange-1", "accepted"));
    assertThat(result.status()).isEqualTo(OrderStatus.SUBMITTED); assertThat(events.value.previousStatus()).isEqualTo(OrderStatus.CREATED);
  }
  @Test void rejects_invalid_and_terminal_transitions() {
    MemoryStore store = new MemoryStore(order(OrderStatus.FILLED)); OrderLifecycleService service = new OrderLifecycleService(store, event -> event, mock(AuditEventWriter.class));
    assertThatThrownBy(() -> service.transition(store.value.id(), new OrderTransitionRequest(OrderStatus.CANCELLED, null, null))).isInstanceOf(OrderTransitionException.class).hasMessageStartingWith("INVALID_TRANSITION");
  }
  private static OrderRecord order(OrderStatus status) { return new OrderRecord(UUID.randomUUID(), "order-1", "bot", "v1", "BTC/USD", RiskDecisionRequest.Side.BUY, BigDecimal.ONE, BigDecimal.TEN, status, Instant.now()); }
  private static final class MemoryStore implements OrderStore {
    OrderRecord value;
    MemoryStore(OrderRecord value) { this.value = value; }
    public Optional<OrderRecord> findByClientOrderId(String id) { return Optional.empty(); }
    public Optional<OrderRecord> findById(UUID id) { return Optional.ofNullable(value != null && value.id().equals(id) ? value : null); }
    public List<OrderRecord> findByBotId(String botId) { return value != null && value.botId().equals(botId) ? List.of(value) : List.of(); }
    public List<OrderRecord> findOpenOrdersByBotId(String botId) { return value != null && value.botId().equals(botId) ? List.of(value) : List.of(); }
    public OrderRecord save(OrderRecord order) { return value = order; }
    public OrderRecord updateStatus(UUID id, OrderStatus status) { return value = new OrderRecord(value.id(), value.clientOrderId(), value.botId(), value.strategyVersionId(), value.symbol(), value.side(), value.quantity(), value.referencePrice(), status, value.createdAt()); }
  }
  private static final class MemoryEvents implements OrderEventStore { OrderEvent value; public OrderEvent append(OrderEvent event) { return value = event; } }
}
