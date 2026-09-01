package io.algopilot.agent.execution;

import java.time.Instant;
import java.util.UUID;

public record StrategyValidationResult(
    UUID id,
    UUID intentId,
    UUID decisionId,
    boolean passed,
    String reason,
    Instant evaluatedAt
) {}
