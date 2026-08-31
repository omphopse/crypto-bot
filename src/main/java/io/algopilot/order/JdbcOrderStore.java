package io.algopilot.order;

import io.algopilot.risk.RiskDecisionRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderStore implements OrderStore {
  private final JdbcTemplate jdbc;
  public JdbcOrderStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Optional<OrderRecord> findByClientOrderId(String clientOrderId) {
    return jdbc.query("select * from orders where client_order_id = ?", this::map, clientOrderId).stream().findFirst();
  }

  @Override public Optional<OrderRecord> findById(UUID id) {
    return jdbc.query("select * from orders where id = ?", this::map, id).stream().findFirst();
  }

  @Override public List<OrderRecord> findByBotId(String botId) {
    return jdbc.query("select * from orders where bot_id = ? order by created_at desc", this::map, botId);
  }

  @Override public List<OrderRecord> findOpenOrdersByBotId(String botId) {
    return jdbc.query("select * from orders where bot_id = ? and status in ('CREATED', 'SUBMITTED', 'ACKNOWLEDGED', 'PARTIALLY_FILLED', 'CANCEL_REQUESTED') order by created_at desc", this::map, botId);
  }

  @Override public List<OrderRecord> findAll(int limit) {
    return jdbc.query("select * from orders order by created_at desc limit ?", this::map, Math.max(1, limit));
  }

  @Override public OrderRecord save(OrderRecord order) {
    jdbc.update("insert into orders (id, client_order_id, bot_id, strategy_version_id, symbol, side, quantity, reference_price, status, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        order.id(), order.clientOrderId(), order.botId(), order.strategyVersionId(), order.symbol(), order.side().name(), order.quantity(), order.referencePrice(), order.status().name(), order.createdAt());
    return order;
  }

  @Override public OrderRecord updateStatus(UUID id, OrderStatus status) {
    jdbc.update("update orders set status = ? where id = ?", status.name(), id);
    return findById(id).orElseThrow(() -> new OrderNotFoundException(id));
  }

  private OrderRecord map(ResultSet rs, int row) throws SQLException {
    return new OrderRecord(
        rs.getObject("id", java.util.UUID.class),
        rs.getString("client_order_id"),
        rs.getString("bot_id"),
        rs.getString("strategy_version_id"),
        rs.getString("symbol"),
        RiskDecisionRequest.Side.valueOf(rs.getString("side")),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("reference_price"),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
