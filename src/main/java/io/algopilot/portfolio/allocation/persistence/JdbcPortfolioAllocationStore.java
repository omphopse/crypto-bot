package io.algopilot.portfolio.allocation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.portfolio.allocation.model.AllocationWeight;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPortfolioAllocationStore implements PortfolioAllocationStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcPortfolioAllocationStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional
  public void savePlan(PortfolioAllocationPlan plan) {
    String sql = """
        INSERT INTO portfolio_allocation_plans (
          id, total_capital, portfolio_volatility, value_at_risk_95, expected_shortfall_95,
          weights_json, rationale, created_at
        ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """;

    String weightsJson;
    try {
      weightsJson = json.writeValueAsString(plan.targetWeights());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize allocation weights", e);
    }

    jdbc.update(
        sql,
        plan.id(),
        plan.totalCapital(),
        plan.portfolioVolatility(),
        plan.valueAtRisk95(),
        plan.expectedShortfall95(),
        weightsJson,
        plan.rationale(),
        Timestamp.from(plan.createdAt())
    );
  }

  @Override
  public Optional<PortfolioAllocationPlan> findPlanById(UUID id) {
    String sql = "SELECT * FROM portfolio_allocation_plans WHERE id = ?";
    List<PortfolioAllocationPlan> list = jdbc.query(sql, (rs, rowNum) -> mapPlanRow(rs), id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public Optional<PortfolioAllocationPlan> findLatestPlan() {
    String sql = "SELECT * FROM portfolio_allocation_plans ORDER BY created_at DESC LIMIT 1";
    List<PortfolioAllocationPlan> list = jdbc.query(sql, (rs, rowNum) -> mapPlanRow(rs));
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<PortfolioAllocationPlan> findRecentPlans(int limit) {
    String sql = "SELECT * FROM portfolio_allocation_plans ORDER BY created_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, rowNum) -> mapPlanRow(rs), limit);
  }

  private PortfolioAllocationPlan mapPlanRow(ResultSet rs) throws SQLException {
    String weightsJson = rs.getString("weights_json");
    List<AllocationWeight> weights = Collections.emptyList();
    if (weightsJson != null) {
      try {
        weights = json.readValue(weightsJson, new TypeReference<List<AllocationWeight>>() {});
      } catch (JsonProcessingException ignored) {}
    }

    return new PortfolioAllocationPlan(
        (UUID) rs.getObject("id"),
        rs.getBigDecimal("total_capital"),
        rs.getBigDecimal("portfolio_volatility"),
        rs.getBigDecimal("value_at_risk_95"),
        rs.getBigDecimal("expected_shortfall_95"),
        weights,
        rs.getString("rationale"),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
