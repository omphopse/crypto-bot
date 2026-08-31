package io.algopilot.fill;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFillStore implements FillStore {
  private final JdbcTemplate jdbc;
  public JdbcFillStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Optional<Fill> findByExchangeFillId(String id) {
    return jdbc.query("select * from fills where exchange_fill_id = ?", this::map, id).stream().findFirst();
  }

  @Override public Fill save(Fill fill) {
    jdbc.update("insert into fills (id, order_id, exchange_fill_id, quantity, price, fee, filled_at) values (?, ?, ?, ?, ?, ?, ?)",
        fill.id(), fill.orderId(), fill.exchangeFillId(), fill.quantity(), fill.price(), fill.fee(), java.sql.Timestamp.from(fill.filledAt()));
    return fill;
  }

  @Override public BigDecimal totalQuantityForOrder(UUID id) {
    BigDecimal value = jdbc.queryForObject("select coalesce(sum(quantity), 0) from fills where order_id = ?", BigDecimal.class, id);
    return value == null ? BigDecimal.ZERO : value;
  }

  @Override public List<Fill> findByOrderId(UUID orderId) {
    return jdbc.query("select * from fills where order_id = ? order by filled_at desc", this::map, orderId);
  }

  @Override public List<Fill> findByBotId(String botId) {
    return jdbc.query("select f.* from fills f inner join orders o on f.order_id = o.id where o.bot_id = ? order by f.filled_at desc", this::map, botId);
  }

  private Fill map(ResultSet rs, int row) throws SQLException {
    return new Fill(
        rs.getObject("id", UUID.class),
        rs.getObject("order_id", UUID.class),
        rs.getString("exchange_fill_id"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("price"),
        rs.getBigDecimal("fee"),
        rs.getTimestamp("filled_at").toInstant()
    );
  }
}
