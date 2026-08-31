package io.algopilot.portfolio.allocation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioAllocationPlan(
    UUID id,
    BigDecimal totalCapital,
    BigDecimal portfolioVolatility,
    BigDecimal valueAtRisk95,
    BigDecimal expectedShortfall95,
    List<AllocationWeight> targetWeights,
    String rationale,
    Instant createdAt
) {}
