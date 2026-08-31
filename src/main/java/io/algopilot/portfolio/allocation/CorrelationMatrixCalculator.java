package io.algopilot.portfolio.allocation;

import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CorrelationMatrixCalculator {

  private CorrelationMatrixCalculator() {}

  public static List<BigDecimal> calculateReturns(List<Candle> candles) {
    if (candles == null || candles.size() < 2) {
      return Collections.emptyList();
    }
    List<BigDecimal> returns = new ArrayList<>(candles.size() - 1);
    for (int i = 1; i < candles.size(); i++) {
      BigDecimal prev = candles.get(i - 1).close();
      BigDecimal curr = candles.get(i).close();
      if (prev.signum() > 0) {
        BigDecimal ret = curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP);
        returns.add(ret);
      } else {
        returns.add(BigDecimal.ZERO);
      }
    }
    return returns;
  }

  public static BigDecimal calculateMean(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) return BigDecimal.ZERO;
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal v : values) sum = sum.add(v);
    return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateVariance(List<BigDecimal> values) {
    if (values == null || values.size() < 2) return BigDecimal.ZERO;
    BigDecimal mean = calculateMean(values);
    BigDecimal sumSq = BigDecimal.ZERO;
    for (BigDecimal v : values) {
      BigDecimal diff = v.subtract(mean);
      sumSq = sumSq.add(diff.multiply(diff));
    }
    return sumSq.divide(BigDecimal.valueOf(values.size() - 1), 8, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateStdDev(List<BigDecimal> values) {
    BigDecimal variance = calculateVariance(values);
    if (variance.signum() <= 0) return BigDecimal.ZERO;
    return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(8, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateCovariance(List<BigDecimal> a, List<BigDecimal> b) {
    if (a == null || b == null || a.size() < 2 || a.size() != b.size()) {
      return BigDecimal.ZERO;
    }
    BigDecimal meanA = calculateMean(a);
    BigDecimal meanB = calculateMean(b);
    BigDecimal sum = BigDecimal.ZERO;

    for (int i = 0; i < a.size(); i++) {
      BigDecimal diffA = a.get(i).subtract(meanA);
      BigDecimal diffB = b.get(i).subtract(meanB);
      sum = sum.add(diffA.multiply(diffB));
    }
    return sum.divide(BigDecimal.valueOf(a.size() - 1), 8, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateCorrelation(List<BigDecimal> a, List<BigDecimal> b) {
    BigDecimal cov = calculateCovariance(a, b);
    BigDecimal stdA = calculateStdDev(a);
    BigDecimal stdB = calculateStdDev(b);

    if (stdA.signum() <= 0 || stdB.signum() <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    BigDecimal denom = stdA.multiply(stdB);
    return cov.divide(denom, 4, RoundingMode.HALF_UP)
        .max(BigDecimal.valueOf(-1.0))
        .min(BigDecimal.valueOf(1.0));
  }

  public static Map<String, Map<String, BigDecimal>> buildCorrelationMatrix(Map<String, List<BigDecimal>> assetReturns) {
    Map<String, Map<String, BigDecimal>> matrix = new HashMap<>();
    List<String> symbols = new ArrayList<>(assetReturns.keySet());

    for (String s1 : symbols) {
      matrix.putIfAbsent(s1, new HashMap<>());
      for (String s2 : symbols) {
        if (s1.equals(s2)) {
          matrix.get(s1).put(s2, BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
        } else {
          BigDecimal corr = calculateCorrelation(assetReturns.get(s1), assetReturns.get(s2));
          matrix.get(s1).put(s2, corr);
        }
      }
    }
    return matrix;
  }
}
