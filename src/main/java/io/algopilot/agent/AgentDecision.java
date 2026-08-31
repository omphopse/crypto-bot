package io.algopilot.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentDecision(UUID id, UUID botId, UUID strategyVersionId, DecisionAction action, String symbol,
                            String payload, Instant decidedAt) {}
