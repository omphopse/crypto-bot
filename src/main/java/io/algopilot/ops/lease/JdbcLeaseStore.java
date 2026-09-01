package io.algopilot.ops.lease;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLeaseStore implements LeaseStore {
  private final JdbcTemplate jdbc;

  public JdbcLeaseStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean tryAcquireLease(BotRuntimeLease lease) {
    // Insert if no lease exists, or overwrite if existing lease has expired
    int updated = jdbc.update(
        "INSERT INTO bot_runtime_leases (bot_id, instance_id, lease_id, acquired_at, expires_at, heartbeat_at) " +
        "VALUES (?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (bot_id) DO UPDATE SET " +
        "instance_id = EXCLUDED.instance_id, " +
        "lease_id = EXCLUDED.lease_id, " +
        "acquired_at = EXCLUDED.acquired_at, " +
        "expires_at = EXCLUDED.expires_at, " +
        "heartbeat_at = EXCLUDED.heartbeat_at " +
        "WHERE bot_runtime_leases.expires_at < ?",
        lease.botId(), lease.instanceId(), lease.leaseId(),
        Timestamp.from(lease.acquiredAt()), Timestamp.from(lease.expiresAt()),
        Timestamp.from(lease.heartbeatAt()), Timestamp.from(lease.acquiredAt())
    );
    return updated > 0;
  }

  @Override
  public boolean tryRenewLease(UUID botId, UUID leaseId, String instanceId, Instant newExpiresAt, Instant heartbeatAt) {
    int updated = jdbc.update(
        "UPDATE bot_runtime_leases SET expires_at = ?, heartbeat_at = ? " +
        "WHERE bot_id = ? AND lease_id = ? AND instance_id = ? AND expires_at > ?",
        Timestamp.from(newExpiresAt), Timestamp.from(heartbeatAt),
        botId, leaseId, instanceId, Timestamp.from(heartbeatAt)
    );
    return updated > 0;
  }

  @Override
  public void releaseLease(UUID botId, UUID leaseId) {
    jdbc.update("DELETE FROM bot_runtime_leases WHERE bot_id = ? AND lease_id = ?", botId, leaseId);
  }

  @Override
  public Optional<BotRuntimeLease> findLeaseByBotId(UUID botId) {
    return jdbc.query("SELECT * FROM bot_runtime_leases WHERE bot_id = ?", this::mapLeaseRow, botId).stream().findFirst();
  }

  @Override
  public List<BotRuntimeLease> findAllActiveLeases(Instant now) {
    return jdbc.query("SELECT * FROM bot_runtime_leases WHERE expires_at >= ?", this::mapLeaseRow, Timestamp.from(now));
  }

  private BotRuntimeLease mapLeaseRow(ResultSet rs, int rowNum) throws SQLException {
    return new BotRuntimeLease(
        (UUID) rs.getObject("bot_id"),
        rs.getString("instance_id"),
        (UUID) rs.getObject("lease_id"),
        rs.getTimestamp("acquired_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("heartbeat_at").toInstant()
    );
  }
}
