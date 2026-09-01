package io.algopilot.ops.health;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcHeartbeatStore implements HeartbeatStore {
  private final JdbcTemplate jdbc;

  public JdbcHeartbeatStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public HeartbeatRecord recordHeartbeat(HeartbeatRecord r) {
    jdbc.update(
        "INSERT INTO component_heartbeats (" +
        "id, component, instance_id, bot_id, sequence_number, status, timestamp, metadata_json) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (component, instance_id, bot_id) DO UPDATE SET " +
        "sequence_number = EXCLUDED.sequence_number, " +
        "status = EXCLUDED.status, " +
        "timestamp = EXCLUDED.timestamp, " +
        "metadata_json = EXCLUDED.metadata_json",
        r.id(), r.component().name(), r.instanceId(), r.botId(), r.sequenceNumber(),
        r.status().name(), Timestamp.from(r.timestamp()), r.metadataJson()
    );
    return r;
  }

  @Override
  public Optional<HeartbeatRecord> findLatestHeartbeat(ComponentType component, String instanceId, UUID botId) {
    String sql = botId != null
        ? "SELECT * FROM component_heartbeats WHERE component = ? AND instance_id = ? AND bot_id = ?"
        : "SELECT * FROM component_heartbeats WHERE component = ? AND instance_id = ? AND bot_id IS NULL";

    Object[] params = botId != null ? new Object[]{component.name(), instanceId, botId} : new Object[]{component.name(), instanceId};

    return jdbc.query(sql, this::mapHeartbeatRow, params).stream().findFirst();
  }

  @Override
  public List<HeartbeatRecord> findAllActiveHeartbeats() {
    return jdbc.query("SELECT * FROM component_heartbeats ORDER BY timestamp DESC", this::mapHeartbeatRow);
  }

  @Override
  public List<HeartbeatRecord> findHeartbeatsByBotId(UUID botId) {
    return jdbc.query("SELECT * FROM component_heartbeats WHERE bot_id = ? ORDER BY timestamp DESC", this::mapHeartbeatRow, botId);
  }

  @Override
  public HealthEvent recordHealthEvent(HealthEvent e) {
    jdbc.update(
        "INSERT INTO health_events (id, component, instance_id, bot_id, event_type, severity, detail, event_timestamp) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        e.id(), e.component().name(), e.instanceId(), e.botId(), e.eventType(), e.severity(), e.detail(), Timestamp.from(e.eventTimestamp())
    );
    return e;
  }

  @Override
  public List<HealthEvent> findRecentHealthEvents(int limit) {
    return jdbc.query("SELECT * FROM health_events ORDER BY event_timestamp DESC LIMIT ?", this::mapHealthEventRow, Math.max(1, limit));
  }

  private HeartbeatRecord mapHeartbeatRow(ResultSet rs, int rowNum) throws SQLException {
    return new HeartbeatRecord(
        (UUID) rs.getObject("id"),
        ComponentType.valueOf(rs.getString("component")),
        rs.getString("instance_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getLong("sequence_number"),
        HealthState.valueOf(rs.getString("status")),
        rs.getTimestamp("timestamp").toInstant(),
        rs.getString("metadata_json")
    );
  }

  private HealthEvent mapHealthEventRow(ResultSet rs, int rowNum) throws SQLException {
    return new HealthEvent(
        (UUID) rs.getObject("id"),
        ComponentType.valueOf(rs.getString("component")),
        rs.getString("instance_id"),
        (UUID) rs.getObject("bot_id"),
        rs.getString("event_type"),
        rs.getString("severity"),
        rs.getString("detail"),
        rs.getTimestamp("event_timestamp").toInstant()
    );
  }
}
