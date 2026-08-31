package io.algopilot.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** A version definition is immutable after creation; updates always create a later version. */
public record StrategyVersion(UUID id, UUID strategyId, int versionNumber, JsonNode definition, String changeReason, Instant createdAt) {}
