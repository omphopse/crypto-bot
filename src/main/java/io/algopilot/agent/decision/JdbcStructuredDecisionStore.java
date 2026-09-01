package io.algopilot.agent.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class JdbcStructuredDecisionStore implements StructuredDecisionStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcStructuredDecisionStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public StructuredTradeDecision save(StructuredTradeDecision d) {
    String evJson = "[]";
    String riskJson = "[]";
    String invJson = "[]";
    try {
      if (d.evidenceReferences() != null) evJson = json.writeValueAsString(d.evidenceReferences());
      if (d.riskFactors() != null) riskJson = json.writeValueAsString(d.riskFactors());
      if (d.invalidationConditions() != null) invJson = json.writeValueAsString(d.invalidationConditions());
    } catch (JsonProcessingException ignored) {}

    jdbc.update(
        "INSERT INTO structured_trade_decisions (" +
        "id, context_id, context_hash, bot_id, session_id, strategy_version_id, " +
        "provider, model, decision, symbol, side, confidence, quantity, reference_price, " +
        "stop_loss, take_profit, time_horizon, thesis, evidence_references, risk_factors, " +
        "invalidation_conditions, validation_status, rejection_reason, latency_ms, " +
        "input_tokens, output_tokens, estimated_cost_usd, decision_timestamp, expires_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?)",
        d.id(), d.contextId(), d.contextHash(), d.botId(), d.sessionId(), d.strategyVersionId(),
        d.provider(), d.model(), d.decision().name(), d.symbol(), d.side(), d.confidence(),
        d.quantity(), d.referencePrice(), d.stopLoss(), d.takeProfit(), d.timeHorizon(), d.thesis(),
        evJson, riskJson, invJson, d.validationStatus().name(), d.rejectionReason(),
        d.latencyMs(), d.inputTokens(), d.outputTokens(), d.estimatedCostUsd(),
        Timestamp.from(d.decisionTimestamp()), Timestamp.from(d.expiresAt())
    );
    return d;
  }

  @Override
  public Optional<StructuredTradeDecision> findLatestByBotId(UUID botId) {
    return jdbc.query(
        "SELECT * FROM structured_trade_decisions WHERE bot_id = ? ORDER BY decision_timestamp DESC LIMIT 1",
        this::mapRow, botId
    ).stream().findFirst();
  }

  @Override
  public List<StructuredTradeDecision> findRecentByBotId(UUID botId, int limit) {
    return jdbc.query(
        "SELECT * FROM structured_trade_decisions WHERE bot_id = ? ORDER BY decision_timestamp DESC LIMIT ?",
        this::mapRow, botId, Math.max(1, limit)
    );
  }

  @Override
  public List<StructuredTradeDecision> findAllRecent(int limit) {
    return jdbc.query(
        "SELECT * FROM structured_trade_decisions ORDER BY decision_timestamp DESC LIMIT ?",
        this::mapRow, Math.max(1, limit)
    );
  }

  @Override
  public Optional<StructuredTradeDecision> findById(UUID id) {
    return jdbc.query(
        "SELECT * FROM structured_trade_decisions WHERE id = ?",
        this::mapRow, id
    ).stream().findFirst();
  }

  private StructuredTradeDecision mapRow(ResultSet rs, int rowNum) throws SQLException {
    try {
      List<UUID> evidenceRefs = json.readValue(rs.getString("evidence_references"), new TypeReference<List<UUID>>() {});
      List<String> riskFactors = json.readValue(rs.getString("risk_factors"), new TypeReference<List<String>>() {});
      List<String> invalidations = json.readValue(rs.getString("invalidation_conditions"), new TypeReference<List<String>>() {});

      return new StructuredTradeDecision(
          (UUID) rs.getObject("id"),
          (UUID) rs.getObject("context_id"),
          rs.getString("context_hash"),
          (UUID) rs.getObject("bot_id"),
          (UUID) rs.getObject("session_id"),
          (UUID) rs.getObject("strategy_version_id"),
          rs.getString("provider"),
          rs.getString("model"),
          TradeAction.valueOf(rs.getString("decision")),
          rs.getString("symbol"),
          rs.getString("side"),
          rs.getBigDecimal("confidence"),
          rs.getBigDecimal("quantity"),
          rs.getBigDecimal("reference_price"),
          rs.getBigDecimal("stop_loss"),
          rs.getBigDecimal("take_profit"),
          rs.getString("time_horizon"),
          rs.getString("thesis"),
          evidenceRefs,
          riskFactors,
          invalidations,
          ValidationStatus.valueOf(rs.getString("validation_status")),
          rs.getString("rejection_reason"),
          rs.getLong("latency_ms"),
          rs.getInt("input_tokens"),
          rs.getInt("output_tokens"),
          rs.getBigDecimal("estimated_cost_usd"),
          rs.getTimestamp("decision_timestamp").toInstant(),
          rs.getTimestamp("expires_at").toInstant()
      );
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to parse decision JSON array metadata", e);
    }
  }
}
