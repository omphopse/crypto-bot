package io.algopilot.agent.context;

import java.math.BigDecimal;

public record PerformanceContext(
    int totalTrades,
    int winningTrades,
    int losingTrades,
    BigDecimal winRatePercent,
    BigDecimal profitFactor,
    BigDecimal netPnl,
    BigDecimal totalFees
) {}
