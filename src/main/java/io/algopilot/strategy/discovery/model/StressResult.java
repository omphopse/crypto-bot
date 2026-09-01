package io.algopilot.strategy.discovery.model;

import java.math.BigDecimal;
import java.util.UUID;

public record StressResult(
    UUID id,
    UUID candidateId,
    String stressType,
    BigDecimal stressMultiplier,
    BigDecimal simulatedNetPnl,
    BigDecimal simulatedNetExpectancy,
    BigDecimal simulatedProfitFactor,
    boolean isProfitable
) {}
