package io.algopilot.portfolio.rebalance.persistence;

import io.algopilot.portfolio.rebalance.model.RebalanceOrder;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRebalanceStore implements RebalanceStore {
  private final JdbcTemplate jdbc;

  public JdbcRebalanceStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void saveRun(RebalanceRun run) {
    String sql = """
        INSERT INTO rebalance_runs (
          id, plan_id, status, max_drift_pct, orders_count, executed_count, failed_count,
          details, started_at, completed_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    jdbc.update(
        sql,
        run.id(),
        run.planId(),
        run.status(),
        run.maxDriftPct(),
        run.ordersCount(),
        run.executedCount(),
        run.failedCount(),
        run.details(),
        Timestamp.from(run.startedAt()),
        run.completedAt() != null ? Timestamp.from(run.completedAt()) : null
    );
  }

  @Override
  @Transactional
  public void updateRun(RebalanceRun run) {
    String sql = """
        UPDATE rebalance_runs
        SET status = ?, orders_count = ?, executed_count = ?, failed_count = ?,
            details = ?, completed_at = ?
        WHERE id = ?
        """;

    jdbc.update(
        sql,
        run.status(),
        run.ordersCount(),
        run.executedCount(),
        run.failedCount(),
        run.details(),
        run.completedAt() != null ? Timestamp.from(run.completedAt()) : null,
        run.id()
    );
  }

  @Override
  public Optional<RebalanceRun> findRunById(UUID id) {
    String sql = "SELECT * FROM rebalance_runs WHERE id = ?";
    List<RebalanceRun> list = jdbc.query(sql, (rs, rowNum) -> mapRunRow(rs), id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Override
  public List<RebalanceRun> findRecentRuns(int limit) {
    String sql = "SELECT * FROM rebalance_runs ORDER BY started_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, rowNum) -> mapRunRow(rs), limit);
  }

  @Override
  @Transactional
  public void saveOrder(RebalanceOrder order) {
    String sql = """
        INSERT INTO rebalance_orders (
          id, rebalance_run_id, bot_id, symbol, side, quantity, price,
          order_id, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    jdbc.update(
        sql,
        order.id(),
        order.rebalanceRunId(),
        order.botId(),
        order.symbol(),
        order.side(),
        order.quantity(),
        order.price(),
        order.orderId(),
        order.status(),
        Timestamp.from(order.createdAt())
    );
  }

  @Override
  public List<RebalanceOrder> findOrdersByRunId(UUID runId) {
    String sql = "SELECT * FROM rebalance_orders WHERE rebalance_run_id = ? ORDER BY created_at ASC";
    return jdbc.query(sql, (rs, rowNum) -> mapOrderRow(rs), runId);
  }

  private RebalanceRun mapRunRow(ResultSet rs) throws SQLException {
    Timestamp completed = rs.getTimestamp("completed_at");
    return new RebalanceRun(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("plan_id"),
        rs.getString("status"),
        rs.getBigDecimal("max_drift_pct"),
        rs.getInt("orders_count"),
        rs.getInt("executed_count"),
        rs.getInt("failed_count"),
        rs.getString("details"),
        rs.getTimestamp("started_at").toInstant(),
        completed != null ? completed.toInstant() : null
    );
  }

  private RebalanceOrder mapOrderRow(ResultSet rs) throws SQLException {
    return new RebalanceOrder(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("rebalance_run_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getString("symbol"),
        rs.getString("side"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("price"),
        (UUID) rs.getObject("order_id"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
