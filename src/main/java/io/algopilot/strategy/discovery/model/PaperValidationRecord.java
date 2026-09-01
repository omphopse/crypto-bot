package io.algopilot.strategy.discovery.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaperValidationRecord(
    UUID id,
    UUID candidateId,
    BigDecimal backtestExpectancy,
    BigDecimal paperExpectancy,
    BigDecimal fillRatePct,
    BigDecimal actualSlippageBps,
    BigDecimal actualFeeBps,
    boolean driftDetected,
    String driftStatus,
    Instant evaluatedAt
) {}
