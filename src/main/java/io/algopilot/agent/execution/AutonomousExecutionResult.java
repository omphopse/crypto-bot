package io.algopilot.agent.execution;

import java.time.Instant;
import java.util.UUID;

public record AutonomousExecutionResult(
    UUID id,
    UUID intentId,
    UUID decisionId,
    UUID riskDecisionId,
    UUID orderId,
    String status,
    String detail,
    Instant executedAt
) {}
