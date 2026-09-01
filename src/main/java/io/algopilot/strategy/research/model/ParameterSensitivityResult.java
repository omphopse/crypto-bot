package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ParameterSensitivityResult(
    UUID id,
    UUID experimentId,
    String parameterName,
    String parameterValue,
    BigDecimal netReturnPct,
    BigDecimal netExpectancy,
    BigDecimal profitFactor,
    BigDecimal maxDrawdownPct,
    String robustnessClassification
) {}
