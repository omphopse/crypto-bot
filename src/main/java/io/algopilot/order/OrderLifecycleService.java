package io.algopilot.order;

import io.algopilot.audit.AuditEventWriter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderLifecycleService {
  private final OrderStore store; private final OrderEventStore events; private final AuditEventWriter audit; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public OrderLifecycleService(OrderStore store, OrderEventStore events, AuditEventWriter audit) { this(store, events, audit, Clock.systemUTC()); }
  OrderLifecycleService(OrderStore store, OrderEventStore events, AuditEventWriter audit, Clock clock) { this.store = store; this.events = events; this.audit = audit; this.clock = clock; }
  @Transactional public OrderRecord transition(UUID orderId, OrderTransitionRequest request) {
    OrderRecord current = store.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    if (!allowed(current.status()).contains(request.targetStatus())) throw new OrderTransitionException("INVALID_TRANSITION:" + current.status() + "_TO_" + request.targetStatus());
    OrderRecord updated = store.updateStatus(orderId, request.targetStatus());
    events.append(new OrderEvent(UUID.randomUUID(), orderId, current.status(), updated.status(), request.exchangeOrderId(), request.detail(), clock.instant()));
    audit.record("EXECUTION", current.botId(), "ORDER_STATUS_CHANGED", "ORDER", orderId.toString(), Map.of("from", current.status().name(), "to", updated.status().name(), "exchangeOrderId", request.exchangeOrderId() == null ? "" : request.exchangeOrderId(), "detail", request.detail() == null ? "" : request.detail()));
    return updated;
  }
  private Set<OrderStatus> allowed(OrderStatus status) {
    return switch (status) {
      case CREATED -> EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.REJECTED, OrderStatus.FAILED, OrderStatus.EXPIRED);
      case SUBMITTED -> EnumSet.of(OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.REJECTED, OrderStatus.FAILED, OrderStatus.CANCEL_REQUESTED, OrderStatus.EXPIRED);
      case ACKNOWLEDGED -> EnumSet.of(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCEL_REQUESTED, OrderStatus.REJECTED, OrderStatus.FAILED, OrderStatus.EXPIRED);
      case PARTIALLY_FILLED -> EnumSet.of(OrderStatus.FILLED, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED, OrderStatus.FAILED);
      case CANCEL_REQUESTED -> EnumSet.of(OrderStatus.CANCELLED, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.FAILED);
      case FILLED, CANCELLED, REJECTED, EXPIRED, FAILED -> EnumSet.noneOf(OrderStatus.class);
    };
  }
}
