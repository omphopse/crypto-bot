package io.algopilot.strategy.research.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.strategy.research.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcExperimentStore implements ExperimentStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcExperimentStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public StrategyExperiment saveExperiment(StrategyExperiment e) {
    jdbc.update(
        "INSERT INTO strategy_experiments (" +
        "id, strategy_id, strategy_version_id, symbol, timeframe, start_date, end_date, initial_capital, " +
        "slippage_model, slippage_bps, maker_fee_bps, taker_fee_bps, fixed_spread, simulated_latency_ms, " +
        "market_data_source, status, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "status = EXCLUDED.status",
        e.id(), e.strategyId(), e.strategyVersionId(), e.symbol(), e.timeframe(),
        Timestamp.from(e.startDate()), Timestamp.from(e.endDate()), e.initialCapital(),
        e.slippageModel().name(), e.slippageBps(), e.makerFeeBps(), e.takerFeeBps(),
        e.fixedSpread(), e.simulatedLatencyMs(), e.marketDataSource(), e.status().name(),
        Timestamp.from(e.createdAt())
    );
    return e;
  }

  @Override
  public Optional<StrategyExperiment> findExperimentById(UUID id) {
    return jdbc.query("SELECT * FROM strategy_experiments WHERE id = ?", this::mapExperimentRow, id).stream().findFirst();
  }

  @Override
  public List<StrategyExperiment> findAllExperiments() {
    return jdbc.query("SELECT * FROM strategy_experiments ORDER BY created_at DESC", this::mapExperimentRow);
  }

  @Override
  public List<StrategyExperiment> findExperimentsByStrategyId(UUID strategyId) {
    return jdbc.query("SELECT * FROM strategy_experiments WHERE strategy_id = ? ORDER BY created_at DESC", this::mapExperimentRow, strategyId);
  }

  @Override
  public ExperimentMetrics saveMetrics(ExperimentMetrics m) {
    String warningsJson = "[]";
    try {
      if (m.warnings() != null) {
        warningsJson = objectMapper.writeValueAsString(m.warnings());
      }
    } catch (Exception ignored) {}

    jdbc.update(
        "INSERT INTO experiment_metrics (" +
        "experiment_id, gross_pnl, net_pnl, total_return_pct, cagr, win_rate_pct, avg_win, avg_loss, " +
        "profit_factor, gross_expectancy, net_expectancy, max_drawdown_pct, sharpe_ratio, sortino_ratio, " +
        "calmar_ratio, recovery_factor, total_trades, avg_holding_time_ms, total_fees, total_slippage, " +
        "total_spread_cost, total_ai_cost, economic_net_result, robustness_score, warnings_json) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (experiment_id) DO UPDATE SET " +
        "gross_pnl = EXCLUDED.gross_pnl, net_pnl = EXCLUDED.net_pnl, total_return_pct = EXCLUDED.total_return_pct, " +
        "cagr = EXCLUDED.cagr, win_rate_pct = EXCLUDED.win_rate_pct, avg_win = EXCLUDED.avg_win, avg_loss = EXCLUDED.avg_loss, " +
        "profit_factor = EXCLUDED.profit_factor, gross_expectancy = EXCLUDED.gross_expectancy, net_expectancy = EXCLUDED.net_expectancy, " +
        "max_drawdown_pct = EXCLUDED.max_drawdown_pct, sharpe_ratio = EXCLUDED.sharpe_ratio, sortino_ratio = EXCLUDED.sortino_ratio, " +
        "calmar_ratio = EXCLUDED.calmar_ratio, recovery_factor = EXCLUDED.recovery_factor, total_trades = EXCLUDED.total_trades, " +
        "avg_holding_time_ms = EXCLUDED.avg_holding_time_ms, total_fees = EXCLUDED.total_fees, total_slippage = EXCLUDED.total_slippage, " +
        "total_spread_cost = EXCLUDED.total_spread_cost, total_ai_cost = EXCLUDED.total_ai_cost, " +
        "economic_net_result = EXCLUDED.economic_net_result, robustness_score = EXCLUDED.robustness_score, warnings_json = EXCLUDED.warnings_json",
        m.experimentId(), m.grossPnl(), m.netPnl(), m.totalReturnPct(), m.cagr(), m.winRatePct(), m.avgWin(), m.avgLoss(),
        m.profitFactor(), m.grossExpectancy(), m.netExpectancy(), m.maxDrawdownPct(), m.sharpeRatio(), m.sortinoRatio(),
        m.calmarRatio(), m.recoveryFactor(), m.totalTrades(), m.avgHoldingTimeMs(), m.totalFees(), m.totalSlippage(),
        m.totalSpreadCost(), m.totalAiCost(), m.economicNetResult(), m.robustnessScore(), warningsJson
    );
    return m;
  }

  @Override
  public Optional<ExperimentMetrics> findMetricsByExperimentId(UUID experimentId) {
    return jdbc.query("SELECT * FROM experiment_metrics WHERE experiment_id = ?", this::mapMetricsRow, experimentId).stream().findFirst();
  }

  @Override
  @Transactional
  public void saveTrades(List<ExperimentTrade> trades) {
    for (ExperimentTrade t : trades) {
      jdbc.update(
          "INSERT INTO experiment_trades (" +
          "id, experiment_id, symbol, side, entry_time, exit_time, entry_price, exit_price, quantity, " +
          "gross_pnl, net_pnl, fee, slippage, spread_cost, exit_reason) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (id) DO NOTHING",
          t.id(), t.experimentId(), t.symbol(), t.side(), Timestamp.from(t.entryTime()),
          Timestamp.from(t.exitTime()), t.entryPrice(), t.exitPrice(), t.quantity(),
          t.grossPnl(), t.netPnl(), t.fee(), t.slippage(), t.spreadCost(), t.exitReason()
      );
    }
  }

  @Override
  public List<ExperimentTrade> findTradesByExperimentId(UUID experimentId) {
    return jdbc.query("SELECT * FROM experiment_trades WHERE experiment_id = ? ORDER BY entry_time ASC", this::mapTradeRow, experimentId);
  }

  @Override
  @Transactional
  public void saveWalkForwardWindows(List<WalkForwardWindow> windows) {
    for (WalkForwardWindow w : windows) {
      jdbc.update(
          "INSERT INTO experiment_walk_forward_windows (" +
          "id, experiment_id, window_index, in_sample_start, in_sample_end, out_of_sample_start, out_of_sample_end, " +
          "in_sample_net_return_pct, out_of_sample_net_return_pct, oos_degradation_ratio) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (id) DO NOTHING",
          w.id(), w.experimentId(), w.windowIndex(), Timestamp.from(w.inSampleStart()),
          Timestamp.from(w.inSampleEnd()), Timestamp.from(w.outOfSampleStart()),
          Timestamp.from(w.outOfSampleEnd()), w.inSampleNetReturnPct(), w.outOfSampleNetReturnPct(), w.oosDegradationRatio()
      );
    }
  }

  @Override
  public List<WalkForwardWindow> findWalkForwardWindowsByExperimentId(UUID experimentId) {
    return jdbc.query("SELECT * FROM experiment_walk_forward_windows WHERE experiment_id = ? ORDER BY window_index ASC", this::mapWindowRow, experimentId);
  }

  @Override
  @Transactional
  public void saveParameterSweeps(List<ParameterSensitivityResult> sweeps) {
    for (ParameterSensitivityResult p : sweeps) {
      jdbc.update(
          "INSERT INTO experiment_parameter_sweeps (" +
          "id, experiment_id, parameter_name, parameter_value, net_return_pct, net_expectancy, profit_factor, max_drawdown_pct, robustness_classification) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (id) DO NOTHING",
          p.id(), p.experimentId(), p.parameterName(), p.parameterValue(), p.netReturnPct(),
          p.netExpectancy(), p.profitFactor(), p.maxDrawdownPct(), p.robustnessClassification()
      );
    }
  }

  @Override
  public List<ParameterSensitivityResult> findParameterSweepsByExperimentId(UUID experimentId) {
    return jdbc.query("SELECT * FROM experiment_parameter_sweeps WHERE experiment_id = ?", this::mapSweepRow, experimentId);
  }

  @Override
  @Transactional
  public void saveRegimeResults(List<RegimeResult> regimes) {
    for (RegimeResult r : regimes) {
      jdbc.update(
          "INSERT INTO experiment_regime_results (" +
          "id, experiment_id, regime, trade_count, net_pnl, win_rate_pct, profit_factor, net_expectancy, max_drawdown_pct) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          "ON CONFLICT (id) DO NOTHING",
          r.id(), r.experimentId(), r.regime(), r.tradeCount(), r.netPnl(), r.winRatePct(),
          r.profitFactor(), r.netExpectancy(), r.maxDrawdownPct()
      );
    }
  }

  @Override
  public List<RegimeResult> findRegimeResultsByExperimentId(UUID experimentId) {
    return jdbc.query("SELECT * FROM experiment_regime_results WHERE experiment_id = ?", this::mapRegimeRow, experimentId);
  }

  @Override
  public StrategyHealthMetric saveStrategyHealth(StrategyHealthMetric h) {
    jdbc.update(
        "INSERT INTO strategy_health_metrics (" +
        "id, strategy_id, backtest_expectancy, paper_expectancy, demo_expectancy, drift_ratio, health_status, evaluated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO NOTHING",
        h.id(), h.strategyId(), h.backtestExpectancy(), h.paperExpectancy(), h.demoExpectancy(),
        h.driftRatio(), h.healthStatus(), Timestamp.from(h.evaluatedAt())
    );
    return h;
  }

  @Override
  public Optional<StrategyHealthMetric> findLatestStrategyHealth(UUID strategyId) {
    return jdbc.query(
        "SELECT * FROM strategy_health_metrics WHERE strategy_id = ? ORDER BY evaluated_at DESC LIMIT 1",
        this::mapHealthRow, strategyId
    ).stream().findFirst();
  }

  private StrategyExperiment mapExperimentRow(ResultSet rs, int rowNum) throws SQLException {
    return new StrategyExperiment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("strategy_id"),
        (UUID) rs.getObject("strategy_version_id"),
        rs.getString("symbol"),
        rs.getString("timeframe"),
        rs.getTimestamp("start_date").toInstant(),
        rs.getTimestamp("end_date").toInstant(),
        rs.getBigDecimal("initial_capital"),
        SlippageModel.valueOf(rs.getString("slippage_model")),
        rs.getBigDecimal("slippage_bps"),
        rs.getBigDecimal("maker_fee_bps"),
        rs.getBigDecimal("taker_fee_bps"),
        rs.getBigDecimal("fixed_spread"),
        rs.getLong("simulated_latency_ms"),
        rs.getString("market_data_source"),
        ExperimentStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant()
    );
  }

  private ExperimentMetrics mapMetricsRow(ResultSet rs, int rowNum) throws SQLException {
    List<String> warnings = new ArrayList<>();
    String json = rs.getString("warnings_json");
    if (json != null && !json.isBlank()) {
      try {
        warnings = objectMapper.readValue(json, new TypeReference<List<String>>() {});
      } catch (Exception ignored) {}
    }

    return new ExperimentMetrics(
        (UUID) rs.getObject("experiment_id"),
        rs.getBigDecimal("gross_pnl"),
        rs.getBigDecimal("net_pnl"),
        rs.getBigDecimal("total_return_pct"),
        rs.getBigDecimal("cagr"),
        rs.getBigDecimal("win_rate_pct"),
        rs.getBigDecimal("avg_win"),
        rs.getBigDecimal("avg_loss"),
        rs.getBigDecimal("profit_factor"),
        rs.getBigDecimal("gross_expectancy"),
        rs.getBigDecimal("net_expectancy"),
        rs.getBigDecimal("max_drawdown_pct"),
        rs.getBigDecimal("sharpe_ratio"),
        rs.getBigDecimal("sortino_ratio"),
        rs.getBigDecimal("calmar_ratio"),
        rs.getBigDecimal("recovery_factor"),
        rs.getInt("total_trades"),
        rs.getLong("avg_holding_time_ms"),
        rs.getBigDecimal("total_fees"),
        rs.getBigDecimal("total_slippage"),
        rs.getBigDecimal("total_spread_cost"),
        rs.getBigDecimal("total_ai_cost"),
        rs.getBigDecimal("economic_net_result"),
        rs.getBigDecimal("robustness_score"),
        warnings
    );
  }

  private ExperimentTrade mapTradeRow(ResultSet rs, int rowNum) throws SQLException {
    return new ExperimentTrade(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("experiment_id"),
        rs.getString("symbol"),
        rs.getString("side"),
        rs.getTimestamp("entry_time").toInstant(),
        rs.getTimestamp("exit_time").toInstant(),
        rs.getBigDecimal("entry_price"),
        rs.getBigDecimal("exit_price"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("gross_pnl"),
        rs.getBigDecimal("net_pnl"),
        rs.getBigDecimal("fee"),
        rs.getBigDecimal("slippage"),
        rs.getBigDecimal("spread_cost"),
        rs.getString("exit_reason")
    );
  }

  private WalkForwardWindow mapWindowRow(ResultSet rs, int rowNum) throws SQLException {
    return new WalkForwardWindow(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("experiment_id"),
        rs.getInt("window_index"),
        rs.getTimestamp("in_sample_start").toInstant(),
        rs.getTimestamp("in_sample_end").toInstant(),
        rs.getTimestamp("out_of_sample_start").toInstant(),
        rs.getTimestamp("out_of_sample_end").toInstant(),
        rs.getBigDecimal("in_sample_net_return_pct"),
        rs.getBigDecimal("out_of_sample_net_return_pct"),
        rs.getBigDecimal("oos_degradation_ratio")
    );
  }

  private ParameterSensitivityResult mapSweepRow(ResultSet rs, int rowNum) throws SQLException {
    return new ParameterSensitivityResult(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("experiment_id"),
        rs.getString("parameter_name"),
        rs.getString("parameter_value"),
        rs.getBigDecimal("net_return_pct"),
        rs.getBigDecimal("net_expectancy"),
        rs.getBigDecimal("profit_factor"),
        rs.getBigDecimal("max_drawdown_pct"),
        rs.getString("robustness_classification")
    );
  }

  private RegimeResult mapRegimeRow(ResultSet rs, int rowNum) throws SQLException {
    return new RegimeResult(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("experiment_id"),
        rs.getString("regime"),
        rs.getInt("trade_count"),
        rs.getBigDecimal("net_pnl"),
        rs.getBigDecimal("win_rate_pct"),
        rs.getBigDecimal("profit_factor"),
        rs.getBigDecimal("net_expectancy"),
        rs.getBigDecimal("max_drawdown_pct")
    );
  }

  private StrategyHealthMetric mapHealthRow(ResultSet rs, int rowNum) throws SQLException {
    return new StrategyHealthMetric(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("strategy_id"),
        rs.getBigDecimal("backtest_expectancy"),
        rs.getBigDecimal("paper_expectancy"),
        rs.getBigDecimal("demo_expectancy"),
        rs.getBigDecimal("drift_ratio"),
        rs.getString("health_status"),
        rs.getTimestamp("evaluated_at").toInstant()
    );
  }
}
