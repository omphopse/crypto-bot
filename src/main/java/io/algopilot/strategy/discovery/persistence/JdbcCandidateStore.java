package io.algopilot.strategy.discovery.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.strategy.discovery.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCandidateStore implements CandidateStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcCandidateStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public StrategyCandidate saveCandidate(StrategyCandidate c) {
    String paramsJson = "{}";
    try {
      if (c.parameters() != null) {
        paramsJson = objectMapper.writeValueAsString(c.parameters());
      }
    } catch (Exception ignored) {}

    jdbc.update(
        "INSERT INTO discovery_candidates (" +
        "candidate_id, fingerprint, base_strategy_id, name, family, symbol, timeframe, " +
        "parameters_json, generation_method, status, robustness_classification, robustness_score, " +
        "net_expectancy, profit_factor, max_drawdown_pct, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (candidate_id) DO UPDATE SET " +
        "status = EXCLUDED.status, robustness_classification = EXCLUDED.robustness_classification, " +
        "robustness_score = EXCLUDED.robustness_score, net_expectancy = EXCLUDED.net_expectancy, " +
        "profit_factor = EXCLUDED.profit_factor, max_drawdown_pct = EXCLUDED.max_drawdown_pct",
        c.candidateId(), c.fingerprint(), c.baseStrategyId(), c.name(), c.family().name(),
        c.symbol(), c.timeframe(), paramsJson, c.generationMethod().name(), c.status().name(),
        c.robustnessClassification() != null ? c.robustnessClassification().name() : null,
        c.robustnessScore(), c.netExpectancy(), c.profitFactor(), c.maxDrawdownPct(),
        Timestamp.from(c.createdAt())
    );
    return c;
  }

  @Override
  public Optional<StrategyCandidate> findCandidateById(UUID candidateId) {
    return jdbc.query("SELECT * FROM discovery_candidates WHERE candidate_id = ?", this::mapCandidateRow, candidateId).stream().findFirst();
  }

  @Override
  public Optional<StrategyCandidate> findCandidateByFingerprint(String fingerprint) {
    return jdbc.query("SELECT * FROM discovery_candidates WHERE fingerprint = ?", this::mapCandidateRow, fingerprint).stream().findFirst();
  }

  @Override
  public List<StrategyCandidate> findAllCandidates() {
    return jdbc.query("SELECT * FROM discovery_candidates ORDER BY created_at DESC", this::mapCandidateRow);
  }

  @Override
  public List<StrategyCandidate> findCandidatesByFamily(StrategyFamily family) {
    return jdbc.query("SELECT * FROM discovery_candidates WHERE family = ? ORDER BY created_at DESC", this::mapCandidateRow, family.name());
  }

  @Override
  public List<StrategyCandidate> findRankedCandidates(int limit) {
    return jdbc.query(
        "SELECT * FROM discovery_candidates WHERE status != 'REJECTED' ORDER BY net_expectancy DESC NULLS LAST, robustness_score DESC NULLS LAST LIMIT ?",
        this::mapCandidateRow, limit
    );
  }

  @Override
  @Transactional
  public void saveStressResults(List<StressResult> results) {
    for (StressResult s : results) {
      jdbc.update(
          "INSERT INTO candidate_stress_results (" +
          "id, candidate_id, stress_type, stress_multiplier, simulated_net_pnl, " +
          "simulated_net_expectancy, simulated_profit_factor, is_profitable) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (id) DO NOTHING",
          s.id(), s.candidateId(), s.stressType(), s.stressMultiplier(), s.simulatedNetPnl(),
          s.simulatedNetExpectancy(), s.simulatedProfitFactor(), s.isProfitable()
      );
    }
  }

  @Override
  public List<StressResult> findStressResultsByCandidateId(UUID candidateId) {
    return jdbc.query("SELECT * FROM candidate_stress_results WHERE candidate_id = ?", this::mapStressRow, candidateId);
  }

  @Override
  public PaperValidationRecord savePaperValidation(PaperValidationRecord v) {
    jdbc.update(
        "INSERT INTO candidate_paper_validations (" +
        "id, candidate_id, backtest_expectancy, paper_expectancy, fill_rate_pct, " +
        "actual_slippage_bps, actual_fee_bps, drift_detected, drift_status, evaluated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO NOTHING",
        v.id(), v.candidateId(), v.backtestExpectancy(), v.paperExpectancy(), v.fillRatePct(),
        v.actualSlippageBps(), v.actualFeeBps(), v.driftDetected(), v.driftStatus(), Timestamp.from(v.evaluatedAt())
    );
    return v;
  }

  @Override
  public Optional<PaperValidationRecord> findPaperValidationByCandidateId(UUID candidateId) {
    return jdbc.query("SELECT * FROM candidate_paper_validations WHERE candidate_id = ? ORDER BY evaluated_at DESC LIMIT 1", this::mapPaperRow, candidateId).stream().findFirst();
  }

  private StrategyCandidate mapCandidateRow(ResultSet rs, int rowNum) throws SQLException {
    Map<String, String> params = new HashMap<>();
    String json = rs.getString("parameters_json");
    if (json != null && !json.isBlank()) {
      try {
        params = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      } catch (Exception ignored) {}
    }

    String robStr = rs.getString("robustness_classification");
    RobustnessTag robTag = robStr != null ? RobustnessTag.valueOf(robStr) : null;

    return new StrategyCandidate(
        (UUID) rs.getObject("candidate_id"),
        rs.getString("fingerprint"),
        (UUID) rs.getObject("base_strategy_id"),
        rs.getString("name"),
        StrategyFamily.valueOf(rs.getString("family")),
        rs.getString("symbol"),
        rs.getString("timeframe"),
        params,
        GenerationMethod.valueOf(rs.getString("generation_method")),
        CandidateStatus.valueOf(rs.getString("status")),
        robTag,
        rs.getBigDecimal("robustness_score"),
        rs.getBigDecimal("net_expectancy"),
        rs.getBigDecimal("profit_factor"),
        rs.getBigDecimal("max_drawdown_pct"),
        rs.getTimestamp("created_at").toInstant()
    );
  }

  private StressResult mapStressRow(ResultSet rs, int rowNum) throws SQLException {
    return new StressResult(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("candidate_id"),
        rs.getString("stress_type"),
        rs.getBigDecimal("stress_multiplier"),
        rs.getBigDecimal("simulated_net_pnl"),
        rs.getBigDecimal("simulated_net_expectancy"),
        rs.getBigDecimal("simulated_profit_factor"),
        rs.getBoolean("is_profitable")
    );
  }

  private PaperValidationRecord mapPaperRow(ResultSet rs, int rowNum) throws SQLException {
    return new PaperValidationRecord(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("candidate_id"),
        rs.getBigDecimal("backtest_expectancy"),
        rs.getBigDecimal("paper_expectancy"),
        rs.getBigDecimal("fill_rate_pct"),
        rs.getBigDecimal("actual_slippage_bps"),
        rs.getBigDecimal("actual_fee_bps"),
        rs.getBoolean("drift_detected"),
        rs.getString("drift_status"),
        rs.getTimestamp("evaluated_at").toInstant()
    );
  }
}
