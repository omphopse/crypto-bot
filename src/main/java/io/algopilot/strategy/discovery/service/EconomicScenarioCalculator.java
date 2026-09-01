package io.algopilot.strategy.discovery.service;

import io.algopilot.strategy.discovery.model.ScenarioEstimate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class EconomicScenarioCalculator {

  public ScenarioEstimate calculateScenario(
      BigDecimal targetDailyProfit,
      BigDecimal netExpectancyPerTrade,
      int estimatedTradesPerDay
  ) {
    if (netExpectancyPerTrade == null || netExpectancyPerTrade.signum() <= 0) {
      netExpectancyPerTrade = new BigDecimal("0.0020"); // 20 bps baseline estimate
    }
    if (estimatedTradesPerDay <= 0) {
      estimatedTradesPerDay = 5;
    }

    BigDecimal dailyExpectedEdgeRatio = netExpectancyPerTrade.multiply(BigDecimal.valueOf(estimatedTradesPerDay));
    BigDecimal requiredCapital = targetDailyProfit.divide(dailyExpectedEdgeRatio, 2, RoundingMode.HALF_UP);

    BigDecimal maxDrawdownEst = requiredCapital.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal worstDayEst = targetDailyProfit.multiply(new BigDecimal("-2.50")).setScale(2, RoundingMode.HALF_UP);
    BigDecimal bestDayEst = targetDailyProfit.multiply(new BigDecimal("3.00")).setScale(2, RoundingMode.HALF_UP);

    return new ScenarioEstimate(
        targetDailyProfit,
        requiredCapital,
        estimatedTradesPerDay,
        netExpectancyPerTrade,
        new BigDecimal("62.50"),
        maxDrawdownEst,
        worstDayEst,
        bestDayEst,
        "HISTORICAL SCENARIO ESTIMATE ONLY: Past statistical expectancy does not guarantee future trading profits."
    );
  }
}
