package io.algopilot.research.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.research.factor.FactorScore;
import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.model.StrategyCandidate;
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

@Repository("hypothesisResearchStore")
public class JdbcResearchStore implements ResearchStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcResearchStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional
  public void saveHypothesis(AlphaHypothesis hypothesis) {
    String sql = """
        INSERT INTO alpha_hypotheses (
          id, symbol, timeframe, composite_score, factors_json, rationale, created_at
        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
        """;

    String factorsJson;
    try {
      factorsJson = json.writeValueAsString(hypothesis.factors());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize hypothesis factors", e);
    }

    jdbc.update(
        sql,
        hypothesis.id(),
        hypothesis.symbol(),
        hypothesis.timeframe(),
        hypothesis.compositeScore(),
        factorsJson,
        hypothesis.rationale(),
        Timestamp.from(hypothesis.createdAt())
    );
  }

  @Override
  public Optional<AlphaHypothesis> findHypothesisById(UUID id) {
    String sql = "SELECT * FROM alpha_hypotheses WHERE id = ?";
    List<AlphaHypothesis> list = jdbc.query(sql, (rs, rowNum) -> mapHypothesisRow(rs), id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<AlphaHypothesis> findRecentHypotheses(int limit) {
    String sql = "SELECT * FROM alpha_hypotheses ORDER BY created_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, rowNum) -> mapHypothesisRow(rs), limit);
  }

  @Override
  @Transactional
  public void saveCandidate(StrategyCandidate candidate) {
    String sql = """
        INSERT INTO strategy_candidates (
          id, hypothesis_id, strategy_version_id, backtest_id, walk_forward_id, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    jdbc.update(
        sql,
        candidate.id(),
        candidate.hypothesisId(),
        candidate.strategyVersionId(),
        candidate.backtestId(),
        candidate.walkForwardId(),
        candidate.status(),
        Timestamp.from(candidate.createdAt())
    );
  }

  @Override
  public Optional<StrategyCandidate> findCandidateById(UUID id) {
    String sql = "SELECT * FROM strategy_candidates WHERE id = ?";
    List<StrategyCandidate> list = jdbc.query(sql, (rs, rowNum) -> mapCandidateRow(rs), id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<StrategyCandidate> findRecentCandidates(int limit) {
    String sql = "SELECT * FROM strategy_candidates ORDER BY created_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, rowNum) -> mapCandidateRow(rs), limit);
  }

  private AlphaHypothesis mapHypothesisRow(ResultSet rs) throws SQLException {
    String factorsJson = rs.getString("factors_json");
    List<FactorScore> factors = Collections.emptyList();
    if (factorsJson != null) {
      try {
        factors = json.readValue(factorsJson, new TypeReference<List<FactorScore>>() {});
      } catch (JsonProcessingException ignored) {}
    }

    return new AlphaHypothesis(
        (UUID) rs.getObject("id"),
        rs.getString("symbol"),
        rs.getString("timeframe"),
        rs.getBigDecimal("composite_score"),
        factors,
        rs.getString("rationale"),
        rs.getTimestamp("created_at").toInstant()
    );
  }

  private StrategyCandidate mapCandidateRow(ResultSet rs) throws SQLException {
    return new StrategyCandidate(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("hypothesis_id"),
        (UUID) rs.getObject("strategy_version_id"),
        (UUID) rs.getObject("backtest_id"),
        (UUID) rs.getObject("walk_forward_id"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
