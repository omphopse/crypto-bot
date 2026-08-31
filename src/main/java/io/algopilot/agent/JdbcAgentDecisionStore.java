package io.algopilot.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentDecisionStore implements AgentDecisionStore {
  private final JdbcTemplate jdbc;
  public JdbcAgentDecisionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public AgentDecision save(AgentDecision decision) {
    jdbc.update("insert into agent_decisions (id, bot_id, strategy_version_id, action, symbol, payload, decided_at) values (?, ?, ?, ?, ?, cast(? as jsonb), ?)", decision.id(), decision.botId(), decision.strategyVersionId(), decision.action().name(), decision.symbol(), decision.payload(), java.sql.Timestamp.from(decision.decidedAt()));
    return decision;
  }

  @Override public java.util.List<AgentDecision> findRecent(int limit) {
    return jdbc.query("select id, bot_id, strategy_version_id, action, symbol, payload, decided_at from agent_decisions order by decided_at desc limit ?",
        (rs, rowNum) -> new AgentDecision(
            (java.util.UUID) rs.getObject("id"),
            (java.util.UUID) rs.getObject("bot_id"),
            (java.util.UUID) rs.getObject("strategy_version_id"),
            DecisionAction.valueOf(rs.getString("action")),
            rs.getString("symbol"),
            rs.getString("payload"),
            rs.getTimestamp("decided_at").toInstant()
        ), Math.max(1, limit));
  }
}
