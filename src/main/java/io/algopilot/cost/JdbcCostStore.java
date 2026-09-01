package io.algopilot.cost;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCostStore implements CostStore {
  private final JdbcTemplate jdbc;

  public JdbcCostStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AiCostEvent saveCostEvent(AiCostEvent e) {
    jdbc.update(
        "INSERT INTO ai_cost_events (" +
        "cost_event_id, bot_id, agent_session_id, strategy_id, strategy_version_id, " +
        "context_id, decision_id, provider, model, operation_type, input_tokens, " +
        "output_tokens, total_tokens, estimated_input_cost, estimated_output_cost, " +
        "estimated_total_cost, currency, timestamp, latency_ms, research_cost, token_attribution_json) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        e.costEventId(), e.botId(), e.agentSessionId(), e.strategyId(), e.strategyVersionId(),
        e.contextId(), e.decisionId(), e.provider(), e.model(), e.operationType().name(),
        e.inputTokens(), e.outputTokens(), e.totalTokens(), e.estimatedInputCost(),
        e.estimatedOutputCost(), e.estimatedTotalCost(), e.currency(), Timestamp.from(e.timestamp()),
        e.latencyMs(), e.researchCost(), e.tokenAttributionJson()
    );
    return e;
  }

  @Override
  public List<AiCostEvent> findCostEventsByBotId(UUID botId, int limit) {
    return jdbc.query(
        "SELECT * FROM ai_cost_events WHERE bot_id = ? ORDER BY timestamp DESC LIMIT ?",
        this::mapCostEventRow, botId, Math.max(1, limit)
    );
  }

  @Override
  public List<AiCostEvent> findRecentCostEvents(int limit) {
    return jdbc.query(
        "SELECT * FROM ai_cost_events ORDER BY timestamp DESC LIMIT ?",
        this::mapCostEventRow, Math.max(1, limit)
    );
  }

  @Override
  public void savePricing(AiModelPricing p) {
    jdbc.update(
        "INSERT INTO ai_model_pricing (id, provider, model, input_price_per_million, output_price_per_million, effective_from, effective_to) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "input_price_per_million = EXCLUDED.input_price_per_million, " +
        "output_price_per_million = EXCLUDED.output_price_per_million, " +
        "effective_to = EXCLUDED.effective_to",
        p.id(), p.provider(), p.model(), p.inputPricePerMillion(), p.outputPricePerMillion(),
        Timestamp.from(p.effectiveFrom()), p.effectiveTo() != null ? Timestamp.from(p.effectiveTo()) : null
    );
  }

  @Override
  public Optional<AiModelPricing> findActivePricing(String provider, String model, Instant when) {
    return jdbc.query(
        "SELECT * FROM ai_model_pricing WHERE provider = ? AND model = ? AND effective_from <= ? " +
        "AND (effective_to IS NULL OR effective_to > ?) ORDER BY effective_from DESC LIMIT 1",
        this::mapPricingRow, provider, model, Timestamp.from(when), Timestamp.from(when)
    ).stream().findFirst();
  }

  @Override
  public void saveBudgetPolicy(AiBudgetPolicy p) {
    jdbc.update(
        "INSERT INTO ai_budget_policies (id, tier, target_id, max_cost_per_day, max_cost_per_hour, max_requests_per_day, max_requests_per_hour) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (tier, target_id) DO UPDATE SET " +
        "max_cost_per_day = EXCLUDED.max_cost_per_day, " +
        "max_cost_per_hour = EXCLUDED.max_cost_per_hour, " +
        "max_requests_per_day = EXCLUDED.max_requests_per_day, " +
        "max_requests_per_hour = EXCLUDED.max_requests_per_hour",
        p.id(), p.tier().name(), p.targetId(), p.maxCostPerDay(), p.maxCostPerHour(),
        p.maxRequestsPerDay(), p.maxRequestsPerHour()
    );
  }

  @Override
  public List<AiBudgetPolicy> findBudgetPolicies() {
    return jdbc.query("SELECT * FROM ai_budget_policies", this::mapPolicyRow);
  }

  @Override
  public Optional<AiBudgetPolicy> findBudgetPolicy(BudgetTier tier, String targetId) {
    return jdbc.query(
        "SELECT * FROM ai_budget_policies WHERE tier = ? AND target_id = ?",
        this::mapPolicyRow, tier.name(), targetId
    ).stream().findFirst();
  }

  @Override
  public CostSummary getCostSummaryToday(Instant now) {
    Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
    return computeSummary("SELECT COUNT(*), COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), " +
        "COALESCE(SUM(total_tokens), 0), COALESCE(SUM(estimated_total_cost), 0), COALESCE(SUM(research_cost), 0) " +
        "FROM ai_cost_events WHERE timestamp >= ?", Timestamp.from(startOfDay));
  }

  @Override
  public CostSummary getCostSummaryByBotId(UUID botId, Instant now) {
    return computeSummary("SELECT COUNT(*), COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), " +
        "COALESCE(SUM(total_tokens), 0), COALESCE(SUM(estimated_total_cost), 0), COALESCE(SUM(research_cost), 0) " +
        "FROM ai_cost_events WHERE bot_id = ?", botId);
  }

  @Override
  public CostSummary getCostSummaryByStrategyId(UUID strategyId, Instant now) {
    return computeSummary("SELECT COUNT(*), COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0), " +
        "COALESCE(SUM(total_tokens), 0), COALESCE(SUM(estimated_total_cost), 0), COALESCE(SUM(research_cost), 0) " +
        "FROM ai_cost_events WHERE strategy_id = ?", strategyId);
  }

  private CostSummary computeSummary(String sql, Object... params) {
    return jdbc.queryForObject(sql, (rs, rowNum) -> {
      long reqs = rs.getLong(1);
      long inTokens = rs.getLong(2);
      long outTokens = rs.getLong(3);
      long totTokens = rs.getLong(4);
      BigDecimal aiCost = rs.getBigDecimal(5);
      BigDecimal resCost = rs.getBigDecimal(6);
      BigDecimal grandTot = aiCost.add(resCost);
      return new CostSummary(reqs, inTokens, outTokens, totTokens, aiCost, resCost, grandTot, 0L, 0L, 0.0);
    }, params);
  }

  private AiCostEvent mapCostEventRow(ResultSet rs, int rowNum) throws SQLException {
    return new AiCostEvent(
        (UUID) rs.getObject("cost_event_id"),
        (UUID) rs.getObject("bot_id"),
        (UUID) rs.getObject("agent_session_id"),
        (UUID) rs.getObject("strategy_id"),
        (UUID) rs.getObject("strategy_version_id"),
        (UUID) rs.getObject("context_id"),
        (UUID) rs.getObject("decision_id"),
        rs.getString("provider"),
        rs.getString("model"),
        CostOperationType.valueOf(rs.getString("operation_type")),
        rs.getLong("input_tokens"),
        rs.getLong("output_tokens"),
        rs.getLong("total_tokens"),
        rs.getBigDecimal("estimated_input_cost"),
        rs.getBigDecimal("estimated_output_cost"),
        rs.getBigDecimal("estimated_total_cost"),
        rs.getString("currency"),
        rs.getTimestamp("timestamp").toInstant(),
        rs.getLong("latency_ms"),
        rs.getBigDecimal("research_cost"),
        rs.getString("token_attribution_json")
    );
  }

  private AiModelPricing mapPricingRow(ResultSet rs, int rowNum) throws SQLException {
    return new AiModelPricing(
        (UUID) rs.getObject("id"),
        rs.getString("provider"),
        rs.getString("model"),
        rs.getBigDecimal("input_price_per_million"),
        rs.getBigDecimal("output_price_per_million"),
        rs.getTimestamp("effective_from").toInstant(),
        rs.getTimestamp("effective_to") != null ? rs.getTimestamp("effective_to").toInstant() : null
    );
  }

  private AiBudgetPolicy mapPolicyRow(ResultSet rs, int rowNum) throws SQLException {
    return new AiBudgetPolicy(
        (UUID) rs.getObject("id"),
        BudgetTier.valueOf(rs.getString("tier")),
        rs.getString("target_id"),
        rs.getBigDecimal("max_cost_per_day"),
        rs.getBigDecimal("max_cost_per_hour"),
        rs.getInt("max_requests_per_day"),
        rs.getInt("max_requests_per_hour")
    );
  }
}
