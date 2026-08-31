package io.algopilot.portfolio.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class PortfolioRiskCalculator {
  private static final BigDecimal Z_95 = new BigDecimal("1.6449"); // 95% Confidence Normal quantile
  private static final BigDecimal CVAR_MULTIPLIER_95 = new BigDecimal("2.0627"); // CVaR 95% = phi(1.645)/0.05

  private PortfolioRiskCalculator() {}

  public static BigDecimal calculatePortfolioVolatility(
      Map<String, BigDecimal> weights,
      Map<String, BigDecimal> volatilities,
      Map<String, Map<String, BigDecimal>> correlationMatrix
  ) {
    if (weights == null || weights.isEmpty() || volatilities == null || correlationMatrix == null) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    BigDecimal portfolioVariance = BigDecimal.ZERO;

    for (Map.Entry<String, BigDecimal> entryI : weights.entrySet()) {
      String sI = entryI.getKey();
      BigDecimal wI = entryI.getValue();
      BigDecimal volI = volatilities.getOrDefault(sI, BigDecimal.ZERO);

      for (Map.Entry<String, BigDecimal> entryJ : weights.entrySet()) {
        String sJ = entryJ.getKey();
        BigDecimal wJ = entryJ.getValue();
        BigDecimal volJ = volatilities.getOrDefault(sJ, BigDecimal.ZERO);

        BigDecimal corr = BigDecimal.ONE;
        if (!sI.equals(sJ)) {
          Map<String, BigDecimal> row = correlationMatrix.get(sI);
          corr = row != null ? row.getOrDefault(sJ, BigDecimal.ZERO) : BigDecimal.ZERO;
        }

        BigDecimal term = wI.multiply(wJ).multiply(volI).multiply(volJ).multiply(corr);
        portfolioVariance = portfolioVariance.add(term);
      }
    }

    if (portfolioVariance.signum() <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    double stdDev = Math.sqrt(portfolioVariance.doubleValue());
    return BigDecimal.valueOf(stdDev).setScale(4, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateValueAtRisk95(BigDecimal portfolioVolatility, BigDecimal totalCapital) {
    if (portfolioVolatility == null || totalCapital == null || portfolioVolatility.signum() <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return portfolioVolatility.multiply(Z_95).multiply(totalCapital).setScale(4, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateExpectedShortfall95(BigDecimal portfolioVolatility, BigDecimal totalCapital) {
    if (portfolioVolatility == null || totalCapital == null || portfolioVolatility.signum() <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return portfolioVolatility.multiply(CVAR_MULTIPLIER_95).multiply(totalCapital).setScale(4, RoundingMode.HALF_UP);
  }
}
