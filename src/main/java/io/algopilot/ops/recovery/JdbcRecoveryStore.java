package io.algopilot.ops.recovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecoveryStore implements RecoveryStore {
  private final JdbcTemplate jdbc;

  public JdbcRecoveryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public RecoveryRun saveRun(RecoveryRun r) {
    jdbc.update(
        "INSERT INTO ops_recovery_runs (" +
        "id, bot_id, instance_id, status, trigger_reason, step_details, started_at, completed_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "status = EXCLUDED.status, " +
        "step_details = EXCLUDED.step_details, " +
        "completed_at = EXCLUDED.completed_at",
        r.id(), r.botId(), r.instanceId(), r.status().name(), r.triggerReason(),
        r.stepDetails(), Timestamp.from(r.startedAt()),
        r.completedAt() != null ? Timestamp.from(r.completedAt()) : null
    );
    return r;
  }

  @Override
  public Optional<RecoveryRun> findLatestRunByBotId(UUID botId) {
    return jdbc.query(
        "SELECT * FROM ops_recovery_runs WHERE bot_id = ? ORDER BY started_at DESC LIMIT 1",
        this::mapRunRow, botId
    ).stream().findFirst();
  }

  @Override
  public List<RecoveryRun> findRecentRuns(int limit) {
    return jdbc.query(
        "SELECT * FROM ops_recovery_runs ORDER BY started_at DESC LIMIT ?",
        this::mapRunRow, Math.max(1, limit)
    );
  }

  private RecoveryRun mapRunRow(ResultSet rs, int rowNum) throws SQLException {
    return new RecoveryRun(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("bot_id"),
        rs.getString("instance_id"),
        RecoveryStatus.valueOf(rs.getString("status")),
        rs.getString("trigger_reason"),
        rs.getString("step_details"),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
    );
  }
}
