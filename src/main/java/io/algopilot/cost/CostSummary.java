package io.algopilot.cost;

import java.math.BigDecimal;

public record CostSummary(
    long totalRequests,
    long totalInputTokens,
    long totalOutputTokens,
    long totalTokens,
    BigDecimal totalAiCostUsd,
    BigDecimal totalResearchCostUsd,
    BigDecimal grandTotalCostUsd,
    long cacheHits,
    long cacheMisses,
    double cacheHitRate
) {}
