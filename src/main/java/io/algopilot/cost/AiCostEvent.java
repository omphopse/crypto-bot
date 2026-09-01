package io.algopilot.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiCostEvent(
    UUID costEventId,
    UUID botId,
    UUID agentSessionId,
    UUID strategyId,
    UUID strategyVersionId,
    UUID contextId,
    UUID decisionId,
    String provider,
    String model,
    CostOperationType operationType,
    long inputTokens,
    long outputTokens,
    long totalTokens,
    BigDecimal estimatedInputCost,
    BigDecimal estimatedOutputCost,
    BigDecimal estimatedTotalCost,
    String currency,
    Instant timestamp,
    long latencyMs,
    BigDecimal researchCost,
    String tokenAttributionJson
) {}
