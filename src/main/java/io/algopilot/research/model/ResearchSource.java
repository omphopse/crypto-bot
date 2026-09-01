package io.algopilot.research.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ResearchSource(
    UUID id,
    String domain,
    SourceType sourceType,
    BigDecimal reliabilityScore,
    boolean isAllowed
) {}
