package io.algopilot.agent.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAutonomousExecutionStore implements AutonomousExecutionStore {
  private final JdbcTemplate jdbc;

  public JdbcAutonomousExecutionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ValidatedTradeIntent saveIntent(ValidatedTradeIntent i) {
    jdbc.update(
        "INSERT INTO validated_trade_intents (" +
        "id, decision_id, context_id, context_hash, bot_id, session_id, strategy_id, " +
        "strategy_version_id, symbol, action, side, quantity, reference_price, " +
        "stop_loss, take_profit, time_horizon, created_at, expires_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        i.intentId(), i.decisionId(), i.contextId(), i.contextHash(), i.botId(), i.sessionId(),
        i.strategyId(), i.strategyVersionId(), i.symbol(), i.action().name(), i.side(),
        i.quantity(), i.referencePrice(), i.stopLoss(), i.takeProfit(), i.timeHorizon(),
        Timestamp.from(i.createdAt()), Timestamp.from(i.expiresAt())
    );
    return i;
  }

  @Override
  public StrategyValidationResult saveValidation(StrategyValidationResult r) {
    jdbc.update(
        "INSERT INTO strategy_validation_results (id, intent_id, decision_id, passed, reason, evaluated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?)",
        r.id(), r.intentId(), r.decisionId(), r.passed(), r.reason(), Timestamp.from(r.evaluatedAt())
    );
    return r;
  }

  @Override
  public AutonomousExecutionResult saveExecution(AutonomousExecutionResult e) {
    jdbc.update(
        "INSERT INTO autonomous_execution_results (id, intent_id, decision_id, risk_decision_id, order_id, status, detail, executed_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        e.id(), e.intentId(), e.decisionId(), e.riskDecisionId(), e.orderId(), e.status(), e.detail(), Timestamp.from(e.executedAt())
    );
    return e;
  }

  @Override
  public List<AutonomousExecutionResult> findRecentExecutionsByBotId(UUID botId, int limit) {
    return jdbc.query(
        "SELECT e.* FROM autonomous_execution_results e " +
        "JOIN validated_trade_intents i ON e.intent_id = i.id " +
        "WHERE i.bot_id = ? ORDER BY e.executed_at DESC LIMIT ?",
        this::mapExecutionRow, botId, Math.max(1, limit)
    );
  }

  @Override
  public Optional<AutonomousExecutionResult> findLatestExecutionByBotId(UUID botId) {
    return jdbc.query(
        "SELECT e.* FROM autonomous_execution_results e " +
        "JOIN validated_trade_intents i ON e.intent_id = i.id " +
        "WHERE i.bot_id = ? ORDER BY e.executed_at DESC LIMIT 1",
        this::mapExecutionRow, botId
    ).stream().findFirst();
  }

  private AutonomousExecutionResult mapExecutionRow(ResultSet rs, int rowNum) throws SQLException {
    return new AutonomousExecutionResult(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("intent_id"),
        (UUID) rs.getObject("decision_id"),
        (UUID) rs.getObject("risk_decision_id"),
        (UUID) rs.getObject("order_id"),
        rs.getString("status"),
        rs.getString("detail"),
        rs.getTimestamp("executed_at").toInstant()
    );
  }
}
