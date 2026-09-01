package io.algopilot.agent.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentStateStore implements AgentStateStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcAgentStateStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public AgentSession saveSession(AgentSession session) {
    String configStr = session.configuration() != null ? session.configuration().toString() : "{}";
    Timestamp stoppedTs = session.stoppedAt() != null ? Timestamp.from(session.stoppedAt()) : null;

    int updated = jdbc.update(
        "UPDATE agent_sessions SET current_state = ?, mode = ?, configuration = cast(? as jsonb), " +
        "last_heartbeat_at = ?, stopped_at = ? WHERE id = ?",
        session.currentState().name(),
        session.mode().name(),
        configStr,
        Timestamp.from(session.lastHeartbeatAt()),
        stoppedTs,
        session.id()
    );

    if (updated == 0) {
      jdbc.update(
          "INSERT INTO agent_sessions (id, bot_id, name, current_state, mode, configuration, started_at, last_heartbeat_at, stopped_at) " +
          "VALUES (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)",
          session.id(),
          session.botId(),
          session.name(),
          session.currentState().name(),
          session.mode().name(),
          configStr,
          Timestamp.from(session.startedAt()),
          Timestamp.from(session.lastHeartbeatAt()),
          stoppedTs
      );
    }
    return session;
  }

  @Override
  public Optional<AgentSession> findSessionById(UUID id) {
    return jdbc.query("SELECT * FROM agent_sessions WHERE id = ?", this::mapSession, id).stream().findFirst();
  }

  @Override
  public Optional<AgentSession> findActiveSessionByBotId(UUID botId) {
    return jdbc.query(
        "SELECT * FROM agent_sessions WHERE bot_id = ? AND current_state NOT IN ('STOPPED') ORDER BY started_at DESC LIMIT 1",
        this::mapSession, botId
    ).stream().findFirst();
  }

  @Override
  public List<AgentSession> findSessionsByBotId(UUID botId) {
    return jdbc.query("SELECT * FROM agent_sessions WHERE bot_id = ? ORDER BY started_at DESC", this::mapSession, botId);
  }

  @Override
  public List<AgentSession> findAllSessions() {
    return jdbc.query("SELECT * FROM agent_sessions ORDER BY started_at DESC", this::mapSession);
  }

  @Override
  public AgentStateEvent saveStateEvent(AgentStateEvent event) {
    String metaStr = event.metadata() != null ? event.metadata().toString() : "{}";
    jdbc.update(
        "INSERT INTO agent_state_events (id, session_id, bot_id, from_state, to_state, reason, metadata, timestamp) " +
        "VALUES (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)",
        event.id(),
        event.sessionId(),
        event.botId(),
        event.fromState().name(),
        event.toState().name(),
        event.reason(),
        metaStr,
        Timestamp.from(event.timestamp())
    );
    return event;
  }

  @Override
  public List<AgentStateEvent> findStateEventsBySessionId(UUID sessionId, int limit) {
    return jdbc.query(
        "SELECT * FROM agent_state_events WHERE session_id = ? ORDER BY timestamp DESC LIMIT ?",
        this::mapEvent, sessionId, Math.max(1, limit)
    );
  }

  @Override
  public List<AgentStateEvent> findStateEventsByBotId(UUID botId, int limit) {
    return jdbc.query(
        "SELECT * FROM agent_state_events WHERE bot_id = ? ORDER BY timestamp DESC LIMIT ?",
        this::mapEvent, botId, Math.max(1, limit)
    );
  }

  private AgentSession mapSession(ResultSet rs, int rowNum) throws SQLException {
    try {
      JsonNode configNode = json.readTree(rs.getString("configuration"));
      Timestamp stoppedTs = rs.getTimestamp("stopped_at");
      return new AgentSession(
          rs.getObject("id", UUID.class),
          rs.getObject("bot_id", UUID.class),
          rs.getString("name"),
          AgentState.valueOf(rs.getString("current_state")),
          AutonomousMode.valueOf(rs.getString("mode")),
          configNode,
          rs.getTimestamp("started_at").toInstant(),
          rs.getTimestamp("last_heartbeat_at").toInstant(),
          stoppedTs != null ? stoppedTs.toInstant() : null
      );
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to parse agent session configuration json", e);
    }
  }

  private AgentStateEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
    try {
      JsonNode metaNode = json.readTree(rs.getString("metadata"));
      return new AgentStateEvent(
          rs.getObject("id", UUID.class),
          rs.getObject("session_id", UUID.class),
          rs.getObject("bot_id", UUID.class),
          AgentState.valueOf(rs.getString("from_state")),
          AgentState.valueOf(rs.getString("to_state")),
          rs.getString("reason"),
          metaNode,
          rs.getTimestamp("timestamp").toInstant()
      );
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to parse agent state event metadata json", e);
    }
  }
}
