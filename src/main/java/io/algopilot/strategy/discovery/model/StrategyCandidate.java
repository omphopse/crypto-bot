package io.algopilot.strategy.discovery.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StrategyCandidate(
    UUID candidateId,
    String fingerprint,
    UUID baseStrategyId,
    String name,
    StrategyFamily family,
    String symbol,
    String timeframe,
    Map<String, String> parameters,
    GenerationMethod generationMethod,
    CandidateStatus status,
    RobustnessTag robustnessClassification,
    BigDecimal robustnessScore,
    BigDecimal netExpectancy,
    BigDecimal profitFactor,
    BigDecimal maxDrawdownPct,
    Instant createdAt
) {}
