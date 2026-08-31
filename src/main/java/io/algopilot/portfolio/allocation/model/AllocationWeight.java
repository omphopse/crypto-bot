package io.algopilot.portfolio.allocation.model;

import java.math.BigDecimal;

public record AllocationWeight(
    String symbol,
    BigDecimal targetWeight,
    BigDecimal currentWeight,
    BigDecimal targetCapital,
    BigDecimal currentCapital,
    BigDecimal rebalanceDeltaCapital
) {}
