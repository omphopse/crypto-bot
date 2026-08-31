package io.algopilot.reconciliation.model;

import java.util.List;

public record ReconciliationResult(
    ReconciliationRun run,
    List<ReconciliationMismatch> mismatches
) {
  public boolean isMatched() {
    return run.status() == ReconciliationStatus.MATCHED && (mismatches == null || mismatches.isEmpty());
  }

  public boolean hasCriticalMismatches() {
    return mismatches != null && mismatches.stream().anyMatch(m -> m.severity() == MismatchSeverity.CRITICAL);
  }
}
