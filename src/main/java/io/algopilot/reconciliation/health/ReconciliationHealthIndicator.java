package io.algopilot.reconciliation.health;

import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Contributes operational reconciliation state to Spring Boot Actuator health checks.
 * Distinguishes active critical failures from historical resolved events.
 */
@Component
public class ReconciliationHealthIndicator implements HealthIndicator {
  private final ReconciliationStore store;

  public ReconciliationHealthIndicator(ReconciliationStore store) {
    this.store = store;
  }

  @Override
  public Health health() {
    int unresolvedCritical = store.countUnresolvedMismatches(MismatchSeverity.CRITICAL);
    int unresolvedTotal = store.countUnresolvedMismatches(null);
    List<ReconciliationRun> recentRuns = store.findRecentRuns(1);

    Health.Builder builder;
    if (unresolvedCritical > 0) {
      builder = Health.down()
          .withDetail("reconciliationStatus", "CRITICAL_MISMATCH_ACTIVE")
          .withDetail("unresolvedCriticalMismatches", unresolvedCritical);
    } else {
      builder = Health.up()
          .withDetail("reconciliationStatus", "HEALTHY")
          .withDetail("unresolvedCriticalMismatches", 0);
    }

    builder.withDetail("unresolvedTotalMismatches", unresolvedTotal);

    if (!recentRuns.isEmpty()) {
      ReconciliationRun latest = recentRuns.getFirst();
      builder.withDetail("latestRunId", latest.id().toString())
          .withDetail("latestRunStatus", latest.status().name())
          .withDetail("latestRunCompletedAt", latest.completedAt() != null ? latest.completedAt().toString() : "IN_PROGRESS");
    } else {
      builder.withDetail("latestRunStatus", "NO_RUNS_YET");
    }

    return builder.build();
  }
}
