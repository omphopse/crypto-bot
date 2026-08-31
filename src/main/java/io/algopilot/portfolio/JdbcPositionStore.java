package io.algopilot.portfolio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPositionStore implements PositionStore {
  private final JdbcTemplate jdbc;
  public JdbcPositionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Optional<Position> find(String botId, String symbol) {
    return jdbc.query("select * from positions where bot_id = ? and symbol = ?", this::map, botId, symbol).stream().findFirst();
  }

  @Override public List<Position> findByBotId(String botId) {
    return jdbc.query("select * from positions where bot_id = ? order by symbol", this::map, botId);
  }

  @Override public List<Position> findAll() {
    return jdbc.query("select * from positions order by updated_at desc", this::map);
  }

  @Override public Position save(Position p) {
    jdbc.update("insert into positions (id, bot_id, symbol, quantity, average_entry_price, realized_pnl, updated_at) values (?, ?, ?, ?, ?, ?, ?) on conflict (bot_id, symbol) do update set quantity = excluded.quantity, average_entry_price = excluded.average_entry_price, realized_pnl = excluded.realized_pnl, updated_at = excluded.updated_at",
        p.id(), p.botId(), p.symbol(), p.quantity(), p.averageEntryPrice(), p.realizedPnl(), p.updatedAt());
    return p;
  }

  private Position map(ResultSet rs, int row) throws SQLException {
    return new Position(
        rs.getObject("id", UUID.class),
        rs.getString("bot_id"),
        rs.getString("symbol"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("average_entry_price"),
        rs.getBigDecimal("realized_pnl"),
        rs.getTimestamp("updated_at").toInstant()
    );
  }
}
