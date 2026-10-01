package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import io.algopilot.reconciliation.persistence.JdbcReconciliationStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcReconciliationStoreTest {
  private JdbcTemplate jdbc;
  private ObjectMapper json;
  private JdbcReconciliationStore store;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    json = new ObjectMapper().findAndRegisterModules();
    store = new JdbcReconciliationStore(jdbc, json);
  }

  @Test
  void testSaveRun_executesInsert() {
    ReconciliationRun run = new ReconciliationRun(
        UUID.randomUUID(), "bot-123", Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.STARTED, 0, null, Instant.now(), null, Instant.now()
    );
    store.saveRun(run);
    verify(jdbc).update(startsWith("insert into reconciliation_runs"), eq(run.id()), eq("bot-123"), eq("ALPACA_PAPER"), eq("PAPER"), eq("STARTED"), eq(0), isNull(), eq(java.sql.Timestamp.from(run.startedAt())), isNull(), eq(java.sql.Timestamp.from(run.createdAt())));
  }

  @Test
  void testSaveMismatches_executesBatchInsert() {
    UUID runId = UUID.randomUUID();
    ReconciliationMismatch mismatch = new ReconciliationMismatch(
        UUID.randomUUID(), runId, "bot-123",
        MismatchCategory.POSITION_MISMATCH, MismatchType.POSITION_QUANTITY_MISMATCH,
        MismatchSeverity.CRITICAL, "BTC/USD",
        Map.of("quantity", "2.0"), Map.of("quantity", "0.0"),
        ResolutionState.UNRESOLVED, null, Instant.now()
    );

    store.saveMismatches(List.of(mismatch));
    verify(jdbc).update(startsWith("insert into reconciliation_mismatches"), eq(mismatch.id()), eq(runId), eq("bot-123"), eq("POSITION_MISMATCH"), eq("POSITION_QUANTITY_MISMATCH"), eq("CRITICAL"), eq("BTC/USD"), anyString(), anyString(), eq("UNRESOLVED"), isNull(), eq(java.sql.Timestamp.from(mismatch.createdAt())));
  }

  @Test
  void testResolveAllUnresolvedMismatchesForBot_executesUpdate() {
    Instant now = Instant.now();
    store.resolveAllUnresolvedMismatchesForBot("bot-123", now);
    verify(jdbc).update(startsWith("update reconciliation_mismatches set resolution_state = 'RESOLVED'"), eq(java.sql.Timestamp.from(now)), eq("bot-123"));
  }

  @Test
  void testSaveRecovery_executesInsert() {
    UUID id = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    Instant now = Instant.now();
    store.saveRecovery(id, "bot-123", runId, "operator-1", "COMPLETED", "State matched", now);
    verify(jdbc).update(startsWith("insert into reconciliation_recoveries"), eq(id), eq("bot-123"), eq(runId), eq("operator-1"), eq("COMPLETED"), eq("State matched"), eq(java.sql.Timestamp.from(now)));
  }

  @Test
  void testCountUnresolvedMismatchesByBotId_withSeverity() {
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("bot-123"), eq("CRITICAL"))).thenReturn(2);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("bot-123"), eq("WARNING"))).thenReturn(1);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("bot-123"))).thenReturn(3);

    assertEquals(2, store.countUnresolvedMismatchesByBotId("bot-123", MismatchSeverity.CRITICAL));
    assertEquals(1, store.countUnresolvedMismatchesByBotId("bot-123", MismatchSeverity.WARNING));
    assertEquals(3, store.countUnresolvedMismatchesByBotId("bot-123", null));
    assertEquals(3, store.countUnresolvedMismatchesByBotId("bot-123"));
  }
}
