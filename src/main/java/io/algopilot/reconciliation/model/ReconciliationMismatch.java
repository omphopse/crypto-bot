package io.algopilot.reconciliation.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReconciliationMismatch(
    UUID id,
    UUID runId,
    String botId,
    MismatchCategory category,
    MismatchType mismatchType,
    MismatchSeverity severity,
    String symbol,
    Map<String, Object> localValue,
    Map<String, Object> brokerValue,
    ResolutionState resolutionState,
    Instant resolvedAt,
    Instant createdAt
) {}
