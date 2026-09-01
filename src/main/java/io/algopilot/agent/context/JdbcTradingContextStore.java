package io.algopilot.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTradingContextStore implements TradingContextStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcTradingContextStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public TradingContext save(TradingContext c) {
    String payloadJson = "{}";
    try {
      payloadJson = json.writeValueAsString(c);
    } catch (JsonProcessingException ignored) {}

    jdbc.update(
        "INSERT INTO trading_contexts (id, context_hash, bot_id, session_id, symbol, provider, environment, " +
        "agent_state, autonomous_mode, market_price, portfolio_equity, freshness_status, execution_allowed, payload, generated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)",
        c.contextId(), c.contextHash(), c.botId(), c.agentSessionId(),
        c.market() != null ? c.market().symbol() : "UNKNOWN",
        c.provider(), c.environment(),
        c.agentState().name(), c.autonomousMode().name(),
        c.market() != null ? c.market().lastPrice() : null,
        c.portfolio() != null ? c.portfolio().portfolioEquity() : null,
        c.freshness().overallStatus().name(),
        c.safety().executionAllowed(),
        payloadJson,
        Timestamp.from(c.generatedAt())
    );
    return c;
  }

  @Override
  public Optional<TradingContext> findLatestByBotId(UUID botId) {
    return jdbc.query(
        "SELECT payload FROM trading_contexts WHERE bot_id = ? ORDER BY generated_at DESC LIMIT 1",
        this::mapRow, botId
    ).stream().findFirst();
  }

  @Override
  public List<TradingContext> findRecentByBotId(UUID botId, int limit) {
    return jdbc.query(
        "SELECT payload FROM trading_contexts WHERE bot_id = ? ORDER BY generated_at DESC LIMIT ?",
        this::mapRow, botId, Math.max(1, limit)
    );
  }

  @Override
  public Optional<TradingContext> findById(UUID id) {
    return jdbc.query(
        "SELECT payload FROM trading_contexts WHERE id = ?",
        this::mapRow, id
    ).stream().findFirst();
  }

  private TradingContext mapRow(ResultSet rs, int rowNum) throws SQLException {
    try {
      return json.readValue(rs.getString("payload"), TradingContext.class);
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to deserialize TradingContext JSON", e);
    }
  }
}
