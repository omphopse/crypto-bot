package io.algopilot.research.model;

import io.algopilot.research.factor.FactorScore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlphaHypothesis(
    UUID id,
    String symbol,
    String timeframe,
    BigDecimal compositeScore,
    List<FactorScore> factors,
    String rationale,
    Instant createdAt
) {}
