package io.algopilot.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StructuredDecisionRequest(
    @NotNull UUID botId,
    @NotNull UUID strategyVersionId,
    @NotNull DecisionAction action,
    String symbol,
    @DecimalMin(value = "0", inclusive = true) @DecimalMax(value = "1") BigDecimal confidence,
    @DecimalMin(value = "0.00000001") BigDecimal quantity,
    @NotBlank String thesis,
    @NotNull List<@Valid DecisionEvidence> evidence,
    @NotNull List<String> riskFactors,
    @NotNull List<String> invalidationConditions) {}
