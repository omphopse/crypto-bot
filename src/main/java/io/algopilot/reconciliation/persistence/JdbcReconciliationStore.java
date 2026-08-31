package io.algopilot.reconciliation.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.model.ResolutionState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReconciliationStore implements ReconciliationStore {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcReconciliationStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public ReconciliationRun saveRun(ReconciliationRun run) {
    jdbc.update(
        "insert into reconciliation_runs (id, bot_id, broker, execution_mode, status, mismatch_count, error_detail, started_at, completed_at, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        run.id(),
        run.botId(),
        run.broker().name(),
        run.executionMode().name(),
        run.status().name(),
        run.mismatchCount(),
        run.errorDetail(),
        run.startedAt(),
        run.completedAt(),
        run.createdAt()
    );
    return run;
  }

  @Override
  public ReconciliationRun updateRun(ReconciliationRun run) {
    jdbc.update(
        "update reconciliation_runs set status = ?, mismatch_count = ?, error_detail = ?, completed_at = ? where id = ?",
        run.status().name(),
        run.mismatchCount(),
        run.errorDetail(),
        run.completedAt(),
        run.id()
    );
    return run;
  }

  @Override
  public Optional<ReconciliationRun> findRunById(UUID id) {
    return jdbc.query("select * from reconciliation_runs where id = ?", this::mapRun, id).stream().findFirst();
  }

  @Override
  public List<ReconciliationRun> findRecentRuns(int limit) {
    return jdbc.query("select * from reconciliation_runs order by created_at desc limit ?", this::mapRun, limit);
  }

  @Override
  public List<ReconciliationRun> findRunsByBotId(String botId, int limit) {
    return jdbc.query("select * from reconciliation_runs where bot_id = ? order by created_at desc limit ?", this::mapRun, botId, limit);
  }

  @Override
  public Optional<ReconciliationRun> findLatestRunByBotId(String botId) {
    return jdbc.query("select * from reconciliation_runs where bot_id = ? order by created_at desc limit 1", this::mapRun, botId).stream().findFirst();
  }

  @Override
  public void saveMismatches(List<ReconciliationMismatch> mismatches) {
    if (mismatches == null || mismatches.isEmpty()) return;
    for (ReconciliationMismatch m : mismatches) {
      try {
        jdbc.update(
            "insert into reconciliation_mismatches (id, run_id, bot_id, category, mismatch_type, severity, symbol, local_value, broker_value, resolution_state, resolved_at, created_at) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)",
            m.id(),
            m.runId(),
            m.botId(),
            m.category().name(),
            m.mismatchType().name(),
            m.severity().name(),
            m.symbol(),
            json.writeValueAsString(m.localValue()),
            json.writeValueAsString(m.brokerValue()),
            m.resolutionState().name(),
            m.resolvedAt(),
            m.createdAt()
        );
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Mismatch payload cannot be serialized", e);
      }
    }
  }

  @Override
  public List<ReconciliationMismatch> findMismatchesByRunId(UUID runId) {
    return jdbc.query("select * from reconciliation_mismatches where run_id = ? order by created_at desc", this::mapMismatch, runId);
  }

  @Override
  public List<ReconciliationMismatch> findMismatchesByBotId(String botId, ResolutionState resolutionState) {
    if (resolutionState == null) {
      return jdbc.query("select * from reconciliation_mismatches where bot_id = ? order by created_at desc", this::mapMismatch, botId);
    }
    return jdbc.query("select * from reconciliation_mismatches where bot_id = ? and resolution_state = ? order by created_at desc", this::mapMismatch, botId, resolutionState.name());
  }

  @Override
  public List<ReconciliationMismatch> findUnresolvedMismatches(int limit) {
    return jdbc.query("select * from reconciliation_mismatches where resolution_state = 'UNRESOLVED' order by created_at desc limit ?", this::mapMismatch, limit);
  }

  @Override
  public int countUnresolvedMismatches(MismatchSeverity severity) {
    Integer count;
    if (severity == null) {
      count = jdbc.queryForObject("select count(*) from reconciliation_mismatches where resolution_state = 'UNRESOLVED'", Integer.class);
    } else {
      count = jdbc.queryForObject("select count(*) from reconciliation_mismatches where resolution_state = 'UNRESOLVED' and severity = ?", Integer.class, severity.name());
    }
    return count == null ? 0 : count;
  }

  @Override
  public int countUnresolvedMismatchesByBotId(String botId) {
    Integer count = jdbc.queryForObject("select count(*) from reconciliation_mismatches where bot_id = ? and resolution_state = 'UNRESOLVED'", Integer.class, botId);
    return count == null ? 0 : count;
  }

  @Override
  public void updateMismatchResolution(UUID mismatchId, ResolutionState state, Instant resolvedAt) {
    jdbc.update("update reconciliation_mismatches set resolution_state = ?, resolved_at = ? where id = ?", state.name(), resolvedAt, mismatchId);
  }

  @Override
  public void resolveAllUnresolvedMismatchesForBot(String botId, Instant resolvedAt) {
    jdbc.update("update reconciliation_mismatches set resolution_state = 'RESOLVED', resolved_at = ? where bot_id = ? and resolution_state = 'UNRESOLVED'", resolvedAt, botId);
  }

  @Override
  public void saveRecovery(UUID id, String botId, UUID runId, String operatorId, String status, String reason, Instant recoveredAt) {
    jdbc.update(
        "insert into reconciliation_recoveries (id, bot_id, run_id, operator_id, status, reason, recovered_at) values (?, ?, ?, ?, ?, ?, ?)",
        id, botId, runId, operatorId, status, reason, recoveredAt
    );
  }

  private ReconciliationRun mapRun(ResultSet rs, int rowNum) throws SQLException {
    return new ReconciliationRun(
        rs.getObject("id", UUID.class),
        rs.getString("bot_id"),
        Broker.valueOf(rs.getString("broker")),
        ExecutionMode.valueOf(rs.getString("execution_mode")),
        ReconciliationStatus.valueOf(rs.getString("status")),
        rs.getInt("mismatch_count"),
        rs.getString("error_detail"),
        rs.getTimestamp("started_at").toInstant(),
        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
        rs.getTimestamp("created_at").toInstant()
    );
  }

  private ReconciliationMismatch mapMismatch(ResultSet rs, int rowNum) throws SQLException {
    Map<String, Object> localVal = parseMap(rs.getString("local_value"));
    Map<String, Object> brokerVal = parseMap(rs.getString("broker_value"));
    return new ReconciliationMismatch(
        rs.getObject("id", UUID.class),
        rs.getObject("run_id", UUID.class),
        rs.getString("bot_id"),
        MismatchCategory.valueOf(rs.getString("category")),
        MismatchType.valueOf(rs.getString("mismatch_type")),
        MismatchSeverity.valueOf(rs.getString("severity")),
        rs.getString("symbol"),
        localVal,
        brokerVal,
        ResolutionState.valueOf(rs.getString("resolution_state")),
        rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
        rs.getTimestamp("created_at").toInstant()
    );
  }

  private Map<String, Object> parseMap(String jsonString) {
    if (jsonString == null || jsonString.isBlank()) return Collections.emptyMap();
    try {
      return json.readValue(jsonString, MAP_TYPE);
    } catch (JsonProcessingException e) {
      return Map.of("raw", jsonString);
    }
  }
}
