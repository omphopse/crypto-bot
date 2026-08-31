package io.algopilot.risk;

import java.math.BigDecimal;
import java.time.Duration;

public record RiskLimits(BigDecimal maxPositionPercent, BigDecimal maxPortfolioExposurePercent,
                         BigDecimal maxDailyLossPercent, BigDecimal maxDrawdownPercent,
                         BigDecimal maxSpreadPercent, BigDecimal maxSlippagePercent,
                         int maxOpenTrades, int maxTradesPerDay, int maxConsecutiveLosses,
                         Duration maxMarketDataAge) {
  public static RiskLimits defaults() {
    return new RiskLimits(new BigDecimal("10"), new BigDecimal("50"), new BigDecimal("3"),
        new BigDecimal("12"), new BigDecimal("0.30"), new BigDecimal("0.20"), 12, 60, 5, Duration.ofSeconds(15));
  }
}
