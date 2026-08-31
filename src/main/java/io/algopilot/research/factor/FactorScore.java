package io.algopilot.research.factor;

import java.math.BigDecimal;

public record FactorScore(
    String factorName,
    FactorType type,
    BigDecimal rawValue,
    BigDecimal normalizedScore,
    BigDecimal weight,
    String explanation
) {}
