package io.algopilot.strategy.research.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExperimentMetrics(
    UUID experimentId,
    BigDecimal grossPnl,
    BigDecimal netPnl,
    BigDecimal totalReturnPct,
    BigDecimal cagr,
    BigDecimal winRatePct,
    BigDecimal avgWin,
    BigDecimal avgLoss,
    BigDecimal profitFactor,
    BigDecimal grossExpectancy,
    BigDecimal netExpectancy,
    BigDecimal maxDrawdownPct,
    BigDecimal sharpeRatio,
    BigDecimal sortinoRatio,
    BigDecimal calmarRatio,
    BigDecimal recoveryFactor,
    int totalTrades,
    long avgHoldingTimeMs,
    BigDecimal totalFees,
    BigDecimal totalSlippage,
    BigDecimal totalSpreadCost,
    BigDecimal totalAiCost,
    BigDecimal economicNetResult,
    BigDecimal robustnessScore,
    List<String> warnings
) {}
