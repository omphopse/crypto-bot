package io.algopilot.agent.position;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPositionLifecycleStore implements PositionLifecycleStore {
  private final JdbcTemplate jdbc;

  public JdbcPositionLifecycleStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PositionLifecycleRecord saveLifecycle(PositionLifecycleRecord r) {
    jdbc.update(
        "INSERT INTO position_lifecycle_records (" +
        "position_id, bot_id, strategy_version_id, symbol, side, initial_quantity, " +
        "current_quantity, entry_price, initial_stop_loss, current_stop_loss, " +
        "take_profit, trailing_stop_pct, high_water_mark, state, opened_at, closed_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (position_id) DO UPDATE SET " +
        "current_quantity = EXCLUDED.current_quantity, " +
        "current_stop_loss = EXCLUDED.current_stop_loss, " +
        "high_water_mark = EXCLUDED.high_water_mark, " +
        "state = EXCLUDED.state, " +
        "closed_at = EXCLUDED.closed_at, " +
        "updated_at = EXCLUDED.updated_at",
        r.positionId(), r.botId(), r.strategyVersionId(), r.symbol(), r.side(),
        r.initialQuantity(), r.currentQuantity(), r.entryPrice(), r.initialStopLoss(),
        r.currentStopLoss(), r.takeProfit(), r.trailingStopPct(), r.highWaterMark(),
        r.state().name(), Timestamp.from(r.openedAt()),
        r.closedAt() != null ? Timestamp.from(r.closedAt()) : null,
        Timestamp.from(r.updatedAt())
    );
    return r;
  }

  @Override
  public Optional<PositionLifecycleRecord> findLifecycleByPositionId(UUID positionId) {
    return jdbc.query(
        "SELECT * FROM position_lifecycle_records WHERE position_id = ?",
        this::mapLifecycleRow, positionId
    ).stream().findFirst();
  }

  @Override
  public List<PositionLifecycleRecord> findOpenLifecyclesByBotId(UUID botId) {
    return jdbc.query(
        "SELECT * FROM position_lifecycle_records WHERE bot_id = ? AND state != 'CLOSED'",
        this::mapLifecycleRow, botId
    );
  }

  @Override
  public PositionSnapshot saveSnapshot(PositionSnapshot s) {
    jdbc.update(
        "INSERT INTO position_snapshots (" +
        "id, position_id, bot_id, symbol, quantity, entry_price, market_price, " +
        "market_value, unrealized_pnl, realized_pnl, stop_loss, take_profit, " +
        "trailing_stop, mfe, mae, holding_time_ms, snapshot_timestamp) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        s.id(), s.positionId(), s.botId(), s.symbol(), s.quantity(), s.entryPrice(),
        s.marketPrice(), s.marketValue(), s.unrealizedPnl(), s.realizedPnl(),
        s.stopLoss(), s.takeProfit(), s.trailingStop(), s.mfe(), s.mae(),
        s.holdingTimeMs(), Timestamp.from(s.snapshotTimestamp())
    );
    return s;
  }

  @Override
  public List<PositionSnapshot> findSnapshotsByPositionId(UUID positionId, int limit) {
    return jdbc.query(
        "SELECT * FROM position_snapshots WHERE position_id = ? ORDER BY snapshot_timestamp DESC LIMIT ?",
        this::mapSnapshotRow, positionId, Math.max(1, limit)
    );
  }

  @Override
  public PositionStopRecord saveStopRecord(PositionStopRecord r) {
    jdbc.update(
        "INSERT INTO position_stop_history (id, position_id, bot_id, previous_stop, new_stop, reason, decision_id, modified_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        r.id(), r.positionId(), r.botId(), r.previousStop(), r.newStop(), r.reason(), r.decisionId(), Timestamp.from(r.modifiedAt())
    );
    return r;
  }

  @Override
  public List<PositionStopRecord> findStopHistoryByPositionId(UUID positionId) {
    return jdbc.query(
        "SELECT * FROM position_stop_history WHERE position_id = ? ORDER BY modified_at DESC",
        this::mapStopRow, positionId
    );
  }

  @Override
  public PositionExitEvent saveExitEvent(PositionExitEvent e) {
    jdbc.update(
        "INSERT INTO position_exit_events (id, position_id, bot_id, event_type, exit_reason, quantity, price, realized_pnl, order_id, event_timestamp) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        e.id(), e.positionId(), e.botId(), e.eventType(),
        e.exitReason() != null ? e.exitReason().name() : null,
        e.quantity(), e.price(), e.realizedPnl(), e.orderId(), Timestamp.from(e.eventTimestamp())
    );
    return e;
  }

  @Override
  public List<PositionExitEvent> findExitEventsByPositionId(UUID positionId) {
    return jdbc.query(
        "SELECT * FROM position_exit_events WHERE position_id = ? ORDER BY event_timestamp DESC",
        this::mapExitEventRow, positionId
    );
  }

  private PositionLifecycleRecord mapLifecycleRow(ResultSet rs, int rowNum) throws SQLException {
    return new PositionLifecycleRecord(
        (UUID) rs.getObject("position_id"),
        (UUID) rs.getObject("bot_id"),
        (UUID) rs.getObject("strategy_version_id"),
        rs.getString("symbol"),
        rs.getString("side"),
        rs.getBigDecimal("initial_quantity"),
        rs.getBigDecimal("current_quantity"),
        rs.getBigDecimal("entry_price"),
        rs.getBigDecimal("initial_stop_loss"),
        rs.getBigDecimal("current_stop_loss"),
        rs.getBigDecimal("take_profit"),
        rs.getBigDecimal("trailing_stop_pct"),
        rs.getBigDecimal("high_water_mark"),
        PositionLifecycleState.valueOf(rs.getString("state")),
        rs.getTimestamp("opened_at").toInstant(),
        rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null,
        rs.getTimestamp("updated_at").toInstant()
    );
  }

  private PositionSnapshot mapSnapshotRow(ResultSet rs, int rowNum) throws SQLException {
    return new PositionSnapshot(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("position_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getString("symbol"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("entry_price"),
        rs.getBigDecimal("market_price"),
        rs.getBigDecimal("market_value"),
        rs.getBigDecimal("unrealized_pnl"),
        rs.getBigDecimal("realized_pnl"),
        rs.getBigDecimal("stop_loss"),
        rs.getBigDecimal("take_profit"),
        rs.getBigDecimal("trailing_stop"),
        rs.getBigDecimal("mfe"),
        rs.getBigDecimal("mae"),
        rs.getLong("holding_time_ms"),
        rs.getTimestamp("snapshot_timestamp").toInstant()
    );
  }

  private PositionStopRecord mapStopRow(ResultSet rs, int rowNum) throws SQLException {
    return new PositionStopRecord(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("position_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getBigDecimal("previous_stop"),
        rs.getBigDecimal("new_stop"),
        rs.getString("reason"),
        (UUID) rs.getObject("decision_id"),
        rs.getTimestamp("modified_at").toInstant()
    );
  }

  private PositionExitEvent mapExitEventRow(ResultSet rs, int rowNum) throws SQLException {
    String exitReasonStr = rs.getString("exit_reason");
    return new PositionExitEvent(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("position_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getString("event_type"),
        exitReasonStr != null ? ExitReason.valueOf(exitReasonStr) : null,
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("price"),
        rs.getBigDecimal("realized_pnl"),
        (UUID) rs.getObject("order_id"),
        rs.getTimestamp("event_timestamp").toInstant()
    );
  }
}
