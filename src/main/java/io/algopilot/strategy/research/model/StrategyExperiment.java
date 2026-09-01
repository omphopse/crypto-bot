package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StrategyExperiment(
    UUID id,
    UUID strategyId,
    UUID strategyVersionId,
    String symbol,
    String timeframe,
    Instant startDate,
    Instant endDate,
    BigDecimal initialCapital,
    SlippageModel slippageModel,
    BigDecimal slippageBps,
    BigDecimal makerFeeBps,
    BigDecimal takerFeeBps,
    BigDecimal fixedSpread,
    long simulatedLatencyMs,
    String marketDataSource,
    ExperimentStatus status,
    Instant createdAt
) {}
