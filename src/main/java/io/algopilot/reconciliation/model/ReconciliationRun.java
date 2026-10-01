package io.algopilot.reconciliation.model;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationRun(
    UUID id,
    String botId,
    Broker broker,
    ExecutionMode executionMode,
    ReconciliationStatus status,
    int mismatchCount,
    String errorDetail,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    String brokerAccountId
) {
  public ReconciliationRun(UUID id, String botId, Broker broker, ExecutionMode executionMode,
                           ReconciliationStatus status, int mismatchCount, String errorDetail,
                           Instant startedAt, Instant completedAt, Instant createdAt) {
    this(id, botId, broker, executionMode, status, mismatchCount, errorDetail, startedAt, completedAt, createdAt, null);
  }
}
