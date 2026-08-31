package io.algopilot.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentDecisionStore implements AgentDecisionStore {
  private final JdbcTemplate jdbc;
  public JdbcAgentDecisionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public AgentDecision save(AgentDecision decision) {
    jdbc.update("insert into agent_decisions (id, bot_id, strategy_version_id, action, symbol, payload, decided_at) values (?, ?, ?, ?, ?, cast(? as jsonb), ?)", decision.id(), decision.botId(), decision.strategyVersionId(), decision.action().name(), decision.symbol(), decision.payload(), decision.decidedAt());
    return decision;
  }
}
