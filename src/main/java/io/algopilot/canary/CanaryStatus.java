package io.algopilot.canary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CanaryStatus(
    String provider,
    String mode,
    UUID botId,
    String symbol,
    long totalCycles,
    long totalDecisions,
    long totalOrders,
    long totalFills,
    long totalExits,
    long riskRejections,
    long reconciliations,
    long recoveries,
    long errorCount,
    BigDecimal totalAiCostUsd,
    long totalResearchCalls,
    Instant lastCycleAt,
    String status
) {}
