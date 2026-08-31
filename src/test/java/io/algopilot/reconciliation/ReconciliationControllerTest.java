package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.controller.ReconciliationController;
import io.algopilot.reconciliation.model.BotReconciliationStatus;
import io.algopilot.reconciliation.model.RecoveryRequest;
import io.algopilot.reconciliation.model.RecoveryResult;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.model.RunReconciliationRequest;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationRecoveryService;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ReconciliationControllerTest {
  private ReconciliationService reconciliationService;
  private ReconciliationRecoveryService recoveryService;
  private ReconciliationStore store;
  private ReconciliationController controller;

  @BeforeEach
  void setUp() {
    reconciliationService = mock(ReconciliationService.class);
    recoveryService = mock(ReconciliationRecoveryService.class);
    store = mock(ReconciliationStore.class);
    controller = new ReconciliationController(reconciliationService, recoveryService, store);
  }

  @Test
  void testRun_returnsReconciliationResult() {
    UUID runId = UUID.randomUUID();
    String botId = UUID.randomUUID().toString();
    ReconciliationRun run = new ReconciliationRun(
        runId, botId, Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, Instant.now(), Instant.now(), Instant.now()
    );
    ReconciliationResult result = new ReconciliationResult(run, Collections.emptyList());
    when(reconciliationService.reconcile(botId)).thenReturn(result);

    ResponseEntity<ReconciliationResult> response = controller.run(new RunReconciliationRequest(botId));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isMatched());
  }

  @Test
  void testRecover_returnsRecoveryResult() {
    String botId = UUID.randomUUID().toString();
    RecoveryResult result = new RecoveryResult(UUID.randomUUID(), botId, "COMPLETED", "Recovered", Instant.now());
    when(recoveryService.recover(any(RecoveryRequest.class))).thenReturn(result);

    ResponseEntity<RecoveryResult> response = controller.recover(new RecoveryRequest(botId, "admin", "Tested"));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("COMPLETED", response.getBody().status());
  }

  @Test
  void testListRuns_delegatesToService() {
    ReconciliationRun run = new ReconciliationRun(
        UUID.randomUUID(), "bot-1", Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, Instant.now(), Instant.now(), Instant.now()
    );
    when(reconciliationService.getRecentRuns(10)).thenReturn(List.of(run));

    List<ReconciliationRun> runs = controller.listRuns(10);
    assertEquals(1, runs.size());
  }

  @Test
  void testGetRun_foundAndNotFound() {
    UUID runId = UUID.randomUUID();
    ReconciliationRun run = new ReconciliationRun(
        runId, "bot-1", Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, Instant.now(), Instant.now(), Instant.now()
    );
    when(reconciliationService.getRunById(runId)).thenReturn(Optional.of(run));
    when(reconciliationService.getMismatchesForRun(runId)).thenReturn(Collections.emptyList());

    ResponseEntity<Map<String, Object>> response = controller.getRun(runId);
    assertEquals(HttpStatus.OK, response.getStatusCode());

    UUID unknown = UUID.randomUUID();
    when(reconciliationService.getRunById(unknown)).thenReturn(Optional.empty());
    ResponseEntity<Map<String, Object>> notFoundResponse = controller.getRun(unknown);
    assertEquals(HttpStatus.NOT_FOUND, notFoundResponse.getStatusCode());
  }

  @Test
  void testGetBotStatus_returnsStatus() {
    String botId = UUID.randomUUID().toString();
    BotReconciliationStatus status = new BotReconciliationStatus(
        botId, ReconciliationStatus.MATCHED, 0, 0, false, Instant.now(), null, Collections.emptyList()
    );
    when(reconciliationService.getBotReconciliationStatus(botId)).thenReturn(status);

    ResponseEntity<BotReconciliationStatus> response = controller.getBotStatus(botId);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(botId, response.getBody().botId());
  }

  @Test
  void testExceptionHandler_returnsUnprocessableEntity() {
    ReconciliationException error = new ReconciliationException("EMERGENCY_STOP_CANNOT_RESUME_VIA_RECONCILIATION");
    ResponseEntity<?> response = controller.handleReconciliationError(error);
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
  }
}
