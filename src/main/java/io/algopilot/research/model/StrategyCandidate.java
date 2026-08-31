package io.algopilot.research.model;

import java.time.Instant;
import java.util.UUID;

public record StrategyCandidate(
    UUID id,
    UUID hypothesisId,
    UUID strategyVersionId,
    UUID backtestId,
    UUID walkForwardId,
    String status,
    Instant createdAt
) {}
