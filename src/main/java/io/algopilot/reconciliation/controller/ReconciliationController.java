package io.algopilot.reconciliation.controller;

import io.algopilot.bot.BotNotFoundException;
import io.algopilot.reconciliation.model.BotReconciliationStatus;
import io.algopilot.reconciliation.model.RecoveryRequest;
import io.algopilot.reconciliation.model.RecoveryResult;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.model.RunReconciliationRequest;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationRecoveryService;
import io.algopilot.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
  private final ReconciliationService reconciliationService;
  private final ReconciliationRecoveryService recoveryService;
  private final ReconciliationStore reconciliationStore;

  public ReconciliationController(
      ReconciliationService reconciliationService,
      ReconciliationRecoveryService recoveryService,
      ReconciliationStore reconciliationStore) {
    this.reconciliationService = reconciliationService;
    this.recoveryService = recoveryService;
    this.reconciliationStore = reconciliationStore;
  }

  @PostMapping("/run")
  public ResponseEntity<ReconciliationResult> run(@Valid @RequestBody RunReconciliationRequest request) {
    ReconciliationResult result = reconciliationService.reconcile(request.botId());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/recover")
  public ResponseEntity<RecoveryResult> recover(@Valid @RequestBody RecoveryRequest request) {
    RecoveryResult result = recoveryService.recover(request);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/runs")
  public List<ReconciliationRun> listRuns(@RequestParam(defaultValue = "50") int limit) {
    return reconciliationService.getRecentRuns(limit);
  }

  @GetMapping("/runs/{id}")
  public ResponseEntity<Map<String, Object>> getRun(@PathVariable UUID id) {
    return reconciliationService.getRunById(id)
        .map(run -> {
          List<ReconciliationMismatch> mismatches = reconciliationService.getMismatchesForRun(id);
          return ResponseEntity.ok(Map.<String, Object>of("run", run, "mismatches", mismatches));
        })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/mismatches")
  public List<ReconciliationMismatch> listMismatches(
      @RequestParam(required = false) String botId,
      @RequestParam(required = false) ResolutionState resolutionState,
      @RequestParam(defaultValue = "50") int limit) {
    if (botId != null && !botId.isBlank()) {
      return reconciliationStore.findMismatchesByBotId(botId, resolutionState);
    }
    return reconciliationService.getUnresolvedMismatches(limit);
  }

  @GetMapping("/status/{botId}")
  public ResponseEntity<BotReconciliationStatus> getBotStatus(@PathVariable String botId) {
    return ResponseEntity.ok(reconciliationService.getBotReconciliationStatus(botId));
  }

  @ExceptionHandler({ReconciliationException.class, BotNotFoundException.class})
  public ResponseEntity<?> handleReconciliationError(RuntimeException error) {
    return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage()));
  }
}
