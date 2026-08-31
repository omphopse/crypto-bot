package io.algopilot.reconciliation.model;

import java.time.Instant;
import java.util.List;

public record BotReconciliationStatus(
    String botId,
    ReconciliationStatus latestStatus,
    int unresolvedCriticalCount,
    int unresolvedTotalCount,
    boolean recoveryRequired,
    Instant lastMatchedAt,
    Instant lastMismatchedAt,
    List<ReconciliationMismatch> activeMismatches
) {}
