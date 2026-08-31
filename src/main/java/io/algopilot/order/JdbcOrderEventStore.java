package io.algopilot.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderEventStore implements OrderEventStore {
  private final JdbcTemplate jdbc;
  public JdbcOrderEventStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public OrderEvent append(OrderEvent event) {
    jdbc.update("insert into order_events (id, order_id, previous_status, next_status, exchange_order_id, detail, occurred_at) values (?, ?, ?, ?, ?, ?, ?)", event.id(), event.orderId(), event.previousStatus().name(), event.nextStatus().name(), event.exchangeOrderId(), event.detail(), event.occurredAt());
    return event;
  }
}
