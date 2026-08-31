package io.algopilot.bot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBotStore implements BotStore {
  private final JdbcTemplate jdbc;
  public JdbcBotStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public Bot save(Bot bot) {
    jdbc.update("insert into bots (id, name, strategy_version_id, broker, execution_mode, status, created_at) values (?, ?, ?, ?, ?, ?, ?)", bot.id(), bot.name(), bot.strategyVersionId(), bot.broker().name(), bot.executionMode().name(), bot.status().name(), bot.createdAt());
    return bot;
  }
  @Override public Optional<Bot> findById(UUID id) { return jdbc.query("select * from bots where id = ?", this::map, id).stream().findFirst(); }
  @Override public List<Bot> findAll() { return jdbc.query("select * from bots order by created_at desc", this::map); }
  @Override public Bot updateStatus(UUID id, BotStatus status) { jdbc.update("update bots set status = ? where id = ?", status.name(), id); return findById(id).orElseThrow(() -> new BotNotFoundException(id)); }
  private Bot map(ResultSet rs, int row) throws SQLException { return new Bot(rs.getObject("id", UUID.class), rs.getString("name"), rs.getObject("strategy_version_id", UUID.class), Broker.valueOf(rs.getString("broker")), ExecutionMode.valueOf(rs.getString("execution_mode")), BotStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant()); }
}
