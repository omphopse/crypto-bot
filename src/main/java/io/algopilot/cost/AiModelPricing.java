package io.algopilot.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiModelPricing(
    UUID id,
    String provider,
    String model,
    BigDecimal inputPricePerMillion,
    BigDecimal outputPricePerMillion,
    Instant effectiveFrom,
    Instant effectiveTo
) {}
