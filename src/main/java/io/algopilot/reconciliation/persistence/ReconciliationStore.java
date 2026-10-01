package io.algopilot.reconciliation.persistence;

import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ResolutionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationStore {
  ReconciliationRun saveRun(ReconciliationRun run);
  ReconciliationRun updateRun(ReconciliationRun run);
  Optional<ReconciliationRun> findRunById(UUID id);
  List<ReconciliationRun> findRecentRuns(int limit);
  List<ReconciliationRun> findRunsByBotId(String botId, int limit);
  Optional<ReconciliationRun> findLatestRunByBotId(String botId);

  void saveMismatches(List<ReconciliationMismatch> mismatches);
  List<ReconciliationMismatch> findMismatchesByRunId(UUID runId);
  List<ReconciliationMismatch> findMismatchesByBotId(String botId, ResolutionState resolutionState);
  List<ReconciliationMismatch> findUnresolvedMismatches(int limit);
  int countUnresolvedMismatches(MismatchSeverity severity);
  int countUnresolvedMismatchesByBotId(String botId);
  default int countUnresolvedMismatchesByBotId(String botId, MismatchSeverity severity) {
    return countUnresolvedMismatchesByBotId(botId);
  }
  void updateMismatchResolution(UUID mismatchId, ResolutionState state, Instant resolvedAt);
  void resolveAllUnresolvedMismatchesForBot(String botId, Instant resolvedAt);

  void saveRecovery(UUID id, String botId, UUID runId, String operatorId, String status, String reason, Instant recoveredAt);
}
