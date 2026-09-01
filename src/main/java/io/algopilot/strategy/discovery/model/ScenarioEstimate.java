package io.algopilot.strategy.discovery.model;

import java.math.BigDecimal;

public record ScenarioEstimate(
    BigDecimal targetDailyProfit,
    BigDecimal requiredCapital,
    int estimatedTradesPerDay,
    BigDecimal netExpectancyPerTrade,
    BigDecimal expectedDailyWinRatePct,
    BigDecimal estimatedMaxDrawdown,
    BigDecimal worstDayEstimate,
    BigDecimal bestDayEstimate,
    String disclaimer
) {}
