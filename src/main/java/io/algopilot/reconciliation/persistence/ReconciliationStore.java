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
  default List<ReconciliationMismatch> findMismatchesByBotId(String botId, ResolutionState resolutionState, String brokerAccountId) {
    if (brokerAccountId == null) return findMismatchesByBotId(botId, resolutionState);
    return findMismatchesByBotId(botId, resolutionState).stream()
        .filter(m -> m.brokerAccountId() == null || m.brokerAccountId().equals(brokerAccountId))
        .toList();
  }
  List<ReconciliationMismatch> findUnresolvedMismatches(int limit);
  int countUnresolvedMismatches(MismatchSeverity severity);
  int countUnresolvedMismatchesByBotId(String botId);
  default int countUnresolvedMismatchesByBotId(String botId, MismatchSeverity severity) {
    return countUnresolvedMismatchesByBotId(botId);
  }
  default int countUnresolvedMismatchesByBotId(String botId, MismatchSeverity severity, String brokerAccountId) {
    if (brokerAccountId == null) return countUnresolvedMismatchesByBotId(botId, severity);
    return (int) findMismatchesByBotId(botId, ResolutionState.UNRESOLVED, brokerAccountId).stream()
        .filter(m -> severity == null || m.severity() == severity)
        .count();
  }
  void updateMismatchResolution(UUID mismatchId, ResolutionState state, Instant resolvedAt);
  void resolveAllUnresolvedMismatchesForBot(String botId, Instant resolvedAt);

  void saveRecovery(UUID id, String botId, UUID runId, String operatorId, String status, String reason, Instant recoveredAt);
}
