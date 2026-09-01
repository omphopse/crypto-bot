package io.algopilot.cost;

import java.math.BigDecimal;
import java.util.UUID;

public record AiBudgetPolicy(
    UUID id,
    BudgetTier tier,
    String targetId,
    BigDecimal maxCostPerDay,
    BigDecimal maxCostPerHour,
    int maxRequestsPerDay,
    int maxRequestsPerHour
) {}
