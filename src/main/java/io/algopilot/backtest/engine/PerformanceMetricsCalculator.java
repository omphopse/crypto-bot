package io.algopilot.backtest.engine;

import io.algopilot.backtest.model.EquityPoint;
import io.algopilot.backtest.model.SimulatedTrade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public final class PerformanceMetricsCalculator {
  private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

  private PerformanceMetricsCalculator() {}

  public static BigDecimal calculateMaxDrawdownPct(List<EquityPoint> equityCurve) {
    if (equityCurve == null || equityCurve.isEmpty()) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    BigDecimal peak = equityCurve.get(0).equity();
    BigDecimal maxDrawdownPct = BigDecimal.ZERO;

    for (EquityPoint pt : equityCurve) {
      if (pt.equity().compareTo(peak) > 0) {
        peak = pt.equity();
      } else if (peak.signum() > 0) {
        BigDecimal dd = peak.subtract(pt.equity()).divide(peak, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        if (dd.compareTo(maxDrawdownPct) > 0) {
          maxDrawdownPct = dd;
        }
      }
    }
    return maxDrawdownPct.setScale(4, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateSharpeRatio(List<BigDecimal> periodicReturns, BigDecimal annualRiskFreeRate) {
    if (periodicReturns == null || periodicReturns.size() < 2) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal r : periodicReturns) {
      sum = sum.add(r);
    }
    BigDecimal n = BigDecimal.valueOf(periodicReturns.size());
    BigDecimal mean = sum.divide(n, 8, RoundingMode.HALF_UP);

    BigDecimal varianceSum = BigDecimal.ZERO;
    for (BigDecimal r : periodicReturns) {
      BigDecimal diff = r.subtract(mean);
      varianceSum = varianceSum.add(diff.multiply(diff));
    }
    BigDecimal variance = varianceSum.divide(n.subtract(BigDecimal.ONE), 8, RoundingMode.HALF_UP);
    if (variance.signum() <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    double stdDev = Math.sqrt(variance.doubleValue());
    if (stdDev == 0.0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    BigDecimal dailyRf = annualRiskFreeRate.divide(BigDecimal.valueOf(252), 8, RoundingMode.HALF_UP);
    BigDecimal excessReturn = mean.subtract(dailyRf);
    double annualizedSharpe = Math.sqrt(252.0) * (excessReturn.doubleValue() / stdDev);

    return BigDecimal.valueOf(annualizedSharpe).setScale(4, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateProfitFactor(List<SimulatedTrade> trades) {
    if (trades == null || trades.isEmpty()) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    BigDecimal grossProfit = BigDecimal.ZERO;
    BigDecimal grossLoss = BigDecimal.ZERO;

    for (SimulatedTrade trade : trades) {
      if (trade.pnl().signum() > 0) {
        grossProfit = grossProfit.add(trade.pnl());
      } else if (trade.pnl().signum() < 0) {
        grossLoss = grossLoss.add(trade.pnl().abs());
      }
    }

    if (grossLoss.signum() == 0) {
      return grossProfit.signum() > 0 ? BigDecimal.valueOf(999.99).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    return grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateWinRate(List<SimulatedTrade> trades) {
    if (trades == null || trades.isEmpty()) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    long wins = trades.stream().filter(t -> t.pnl().signum() > 0).count();
    return BigDecimal.valueOf(wins * 100.0)
        .divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP);
  }
}
