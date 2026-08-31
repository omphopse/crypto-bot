package io.algopilot.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Append-only audit sink. Updating or deleting audit history is intentionally unsupported. */
@Service
public class AuditEventWriter {
  private final JdbcTemplate jdbc; private final ObjectMapper json; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public AuditEventWriter(JdbcTemplate jdbc, ObjectMapper json) { this(jdbc, json, Clock.systemUTC()); }
  AuditEventWriter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) { this.jdbc = jdbc; this.json = json; this.clock = clock; }
  public void record(String actorType, String actorId, String type, String aggregateType, String aggregateId, Map<String, ?> payload) {
    try {
      jdbc.update("insert into audit_events (id, occurred_at, actor_type, actor_id, event_type, aggregate_type, aggregate_id, payload) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))", UUID.randomUUID(), clock.instant(), actorType, actorId, type, aggregateType, aggregateId, json.writeValueAsString(payload));
    } catch (JsonProcessingException exception) { throw new IllegalArgumentException("Audit payload cannot be serialized", exception); }
  }
}
