package io.algopilot.risk;

import java.time.Instant;
import java.util.UUID;

public record PersistedRiskDecision(UUID id, String clientOrderId, String botId, String strategyVersionId,
                                    RiskDecision decision, String requestSnapshot, Instant persistedAt) {}
