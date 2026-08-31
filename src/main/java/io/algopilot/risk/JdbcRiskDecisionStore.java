package io.algopilot.risk;

import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRiskDecisionStore implements RiskDecisionStore {
  private final JdbcTemplate jdbc;
  public JdbcRiskDecisionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @Override public PersistedRiskDecision save(PersistedRiskDecision record) {
    String reasons = record.decision().reasons().stream().map(reason -> "\"" + reason.name() + "\"").collect(Collectors.joining(",", "[", "]"));
    jdbc.update("insert into risk_decisions (id, client_order_id, bot_id, strategy_version_id, status, reasons, evaluated_at, request_snapshot) values (?, ?, ?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb))", record.id(), record.clientOrderId(), record.botId(), record.strategyVersionId(), record.decision().status().name(), reasons, record.decision().evaluatedAt(), record.requestSnapshot());
    return record;
  }
}
