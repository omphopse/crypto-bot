package io.algopilot.backtest.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.EquityPoint;
import io.algopilot.backtest.model.SimulatedTrade;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.model.WalkForwardWindowResult;
import io.algopilot.risk.RiskDecisionRequest.Side;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBacktestStore implements BacktestStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcBacktestStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional
  public void saveBacktest(BacktestResult result) {
    String sql = """
        INSERT INTO backtest_runs (
          id, strategy_version_id, symbol, timeframe, start_time, end_time,
          initial_capital, final_equity, total_return_pct, total_trades,
          winning_trades, losing_trades, win_rate, max_drawdown_pct,
          sharpe_ratio, profit_factor, equity_curve, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;

    String equityJson;
    try {
      equityJson = json.writeValueAsString(result.equityCurve());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize equity curve", e);
    }

    jdbc.update(
        sql,
        result.id(),
        result.strategyVersionId(),
        result.symbol(),
        result.timeframe(),
        Timestamp.from(result.startTime()),
        Timestamp.from(result.endTime()),
        result.initialCapital(),
        result.finalEquity(),
        result.totalReturnPct(),
        result.totalTrades(),
        result.winningTrades(),
        result.losingTrades(),
        result.winRate(),
        result.maxDrawdownPct(),
        result.sharpeRatio(),
        result.profitFactor(),
        equityJson,
        Timestamp.from(result.createdAt())
    );

    String tradeSql = """
        INSERT INTO backtest_trades (
          id, backtest_id, symbol, side, entry_time, exit_time,
          entry_price, exit_price, quantity, pnl, fee, return_pct, exit_reason
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    for (SimulatedTrade t : result.trades()) {
      jdbc.update(
          tradeSql,
          t.id(),
          result.id(),
          t.symbol(),
          t.side().name(),
          Timestamp.from(t.entryTime()),
          Timestamp.from(t.exitTime()),
          t.entryPrice(),
          t.exitPrice(),
          t.quantity(),
          t.pnl(),
          t.fee(),
          t.returnPct(),
          t.exitReason()
      );
    }
  }

  @Override
  public Optional<BacktestResult> findBacktestById(UUID id) {
    String sql = "SELECT * FROM backtest_runs WHERE id = ?";
    List<BacktestResult> results = jdbc.query(sql, (rs, rowNum) -> mapBacktestRow(rs), id);
    if (results.isEmpty()) return Optional.empty();

    BacktestResult backtest = results.get(0);
    List<SimulatedTrade> trades = findTradesByBacktestId(id);
    return Optional.of(new BacktestResult(
        backtest.id(), backtest.strategyVersionId(), backtest.symbol(), backtest.timeframe(),
        backtest.startTime(), backtest.endTime(), backtest.initialCapital(), backtest.finalEquity(),
        backtest.totalReturnPct(), backtest.totalTrades(), backtest.winningTrades(), backtest.losingTrades(),
        backtest.winRate(), backtest.maxDrawdownPct(), backtest.sharpeRatio(), backtest.profitFactor(),
        backtest.equityCurve(), trades, backtest.createdAt()
    ));
  }

  @Override
  public List<BacktestResult> findBacktestsByStrategyId(UUID strategyVersionId) {
    String sql = "SELECT * FROM backtest_runs WHERE strategy_version_id = ? ORDER BY created_at DESC";
    return jdbc.query(sql, (rs, rowNum) -> mapBacktestRow(rs), strategyVersionId);
  }

  @Override
  public List<BacktestResult> findRecentBacktests(int limit) {
    String sql = "SELECT * FROM backtest_runs ORDER BY created_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, rowNum) -> mapBacktestRow(rs), limit);
  }

  @Override
  public void saveWalkForward(WalkForwardResult result) {
    String sql = """
        INSERT INTO walk_forward_runs (
          id, strategy_version_id, symbol, timeframe, window_count,
          avg_oos_efficiency, windows_json, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """;

    String windowsJson;
    try {
      windowsJson = json.writeValueAsString(result.windows());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize walk-forward windows", e);
    }

    jdbc.update(
        sql,
        result.id(),
        result.strategyVersionId(),
        result.symbol(),
        result.timeframe(),
        result.windowCount(),
        result.avgOosEfficiency(),
        windowsJson,
        Timestamp.from(result.createdAt())
    );
  }

  @Override
  public Optional<WalkForwardResult> findWalkForwardById(UUID id) {
    String sql = "SELECT * FROM walk_forward_runs WHERE id = ?";
    List<WalkForwardResult> results = jdbc.query(sql, (rs, rowNum) -> {
      String jsonStr = rs.getString("windows_json");
      List<WalkForwardWindowResult> windows = Collections.emptyList();
      if (jsonStr != null) {
        try {
          windows = json.readValue(jsonStr, new TypeReference<List<WalkForwardWindowResult>>() {});
        } catch (JsonProcessingException ignored) {}
      }
      return new WalkForwardResult(
          (UUID) rs.getObject("id"),
          (UUID) rs.getObject("strategy_version_id"),
          rs.getString("symbol"),
          rs.getString("timeframe"),
          rs.getInt("window_count"),
          rs.getBigDecimal("avg_oos_efficiency"),
          windows,
          rs.getTimestamp("created_at").toInstant()
      );
    }, id);

    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  private List<SimulatedTrade> findTradesByBacktestId(UUID backtestId) {
    String sql = "SELECT * FROM backtest_trades WHERE backtest_id = ? ORDER BY entry_time ASC";
    return jdbc.query(sql, (rs, rowNum) -> new SimulatedTrade(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("backtest_id"),
        rs.getString("symbol"),
        Side.valueOf(rs.getString("side")),
        rs.getTimestamp("entry_time").toInstant(),
        rs.getTimestamp("exit_time").toInstant(),
        rs.getBigDecimal("entry_price"),
        rs.getBigDecimal("exit_price"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("pnl"),
        rs.getBigDecimal("fee"),
        rs.getBigDecimal("return_pct"),
        rs.getString("exit_reason")
    ), backtestId);
  }

  private BacktestResult mapBacktestRow(ResultSet rs) throws SQLException {
    String equityCurveJson = rs.getString("equity_curve");
    List<EquityPoint> equityCurve = Collections.emptyList();
    if (equityCurveJson != null) {
      try {
        equityCurve = json.readValue(equityCurveJson, new TypeReference<List<EquityPoint>>() {});
      } catch (JsonProcessingException ignored) {}
    }

    return new BacktestResult(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("strategy_version_id"),
        rs.getString("symbol"),
        rs.getString("timeframe"),
        rs.getTimestamp("start_time").toInstant(),
        rs.getTimestamp("end_time").toInstant(),
        rs.getBigDecimal("initial_capital"),
        rs.getBigDecimal("final_equity"),
        rs.getBigDecimal("total_return_pct"),
        rs.getInt("total_trades"),
        rs.getInt("winning_trades"),
        rs.getInt("losing_trades"),
        rs.getBigDecimal("win_rate"),
        rs.getBigDecimal("max_drawdown_pct"),
        rs.getBigDecimal("sharpe_ratio"),
        rs.getBigDecimal("profit_factor"),
        equityCurve,
        Collections.emptyList(),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
