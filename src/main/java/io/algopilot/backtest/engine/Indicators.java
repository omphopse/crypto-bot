package io.algopilot.backtest.engine;

import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Indicators {
  private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

  private Indicators() {}

  public static List<BigDecimal> sma(List<BigDecimal> values, int period) {
    if (values == null || values.isEmpty() || period <= 0) {
      return Collections.emptyList();
    }
    if (values.size() < period) {
      return new ArrayList<>(Collections.nCopies(values.size(), null));
    }
    List<BigDecimal> result = new ArrayList<>(values.size());
    BigDecimal sum = BigDecimal.ZERO;

    for (int i = 0; i < values.size(); i++) {
      sum = sum.add(values.get(i));
      if (i >= period) {
        sum = sum.subtract(values.get(i - period));
      }
      if (i >= period - 1) {
        result.add(sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP));
      } else {
        result.add(null);
      }
    }
    return result;
  }

  public static List<BigDecimal> ema(List<BigDecimal> values, int period) {
    if (values == null || values.isEmpty() || period <= 0) {
      return Collections.emptyList();
    }
    if (values.size() < period) {
      return new ArrayList<>(Collections.nCopies(values.size(), null));
    }
    List<BigDecimal> result = new ArrayList<>(values.size());
    BigDecimal multiplier = BigDecimal.valueOf(2.0).divide(BigDecimal.valueOf(period + 1.0), 8, RoundingMode.HALF_UP);
    BigDecimal oneMinusMultiplier = BigDecimal.ONE.subtract(multiplier);

    BigDecimal initialSma = BigDecimal.ZERO;
    for (int i = 0; i < period; i++) {
      initialSma = initialSma.add(values.get(i));
      result.add(null);
    }
    BigDecimal currentEma = initialSma.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    result.set(period - 1, currentEma);

    for (int i = period; i < values.size(); i++) {
      currentEma = values.get(i).multiply(multiplier).add(currentEma.multiply(oneMinusMultiplier));
      result.add(currentEma.setScale(8, RoundingMode.HALF_UP));
    }
    return result;
  }

  public static List<BigDecimal> rsi(List<BigDecimal> prices, int period) {
    if (prices == null || prices.isEmpty() || period <= 0) {
      return Collections.emptyList();
    }
    if (prices.size() <= period) {
      return new ArrayList<>(Collections.nCopies(prices.size(), null));
    }
    List<BigDecimal> result = new ArrayList<>(prices.size());
    for (int i = 0; i < period; i++) {
      result.add(null);
    }

    BigDecimal sumGain = BigDecimal.ZERO;
    BigDecimal sumLoss = BigDecimal.ZERO;

    for (int i = 1; i <= period; i++) {
      BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
      if (change.signum() > 0) sumGain = sumGain.add(change);
      else if (change.signum() < 0) sumLoss = sumLoss.add(change.abs());
    }

    BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

    BigDecimal firstRsi = calculateRsiValue(avgGain, avgLoss);
    result.add(firstRsi);

    BigDecimal periodMinusOne = BigDecimal.valueOf(period - 1);
    BigDecimal periodBd = BigDecimal.valueOf(period);

    for (int i = period + 1; i < prices.size(); i++) {
      BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
      BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
      BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;

      avgGain = avgGain.multiply(periodMinusOne).add(gain).divide(periodBd, 8, RoundingMode.HALF_UP);
      avgLoss = avgLoss.multiply(periodMinusOne).add(loss).divide(periodBd, 8, RoundingMode.HALF_UP);

      result.add(calculateRsiValue(avgGain, avgLoss));
    }
    return result;
  }

  public static List<BigDecimal> atr(List<Candle> candles, int period) {
    if (candles == null || candles.isEmpty() || period <= 0) {
      return Collections.emptyList();
    }
    if (candles.size() < period) {
      return new ArrayList<>(Collections.nCopies(candles.size(), null));
    }
    List<BigDecimal> trList = new ArrayList<>(candles.size());
    trList.add(candles.get(0).high().subtract(candles.get(0).low()));

    for (int i = 1; i < candles.size(); i++) {
      Candle current = candles.get(i);
      Candle prev = candles.get(i - 1);

      BigDecimal hl = current.high().subtract(current.low()).abs();
      BigDecimal hc = current.high().subtract(prev.close()).abs();
      BigDecimal lc = current.low().subtract(prev.close()).abs();

      BigDecimal tr = hl.max(hc).max(lc);
      trList.add(tr);
    }

    return sma(trList, period);
  }

  public record BollingerBands(List<BigDecimal> upper, List<BigDecimal> middle, List<BigDecimal> lower) {}

  public record MacdResult(List<BigDecimal> macd, List<BigDecimal> signal, List<BigDecimal> histogram) {}

  public static BollingerBands bollingerBands(List<BigDecimal> prices, int period, double stdDevMultiplier) {
    if (prices == null || prices.isEmpty() || period <= 0) {
      return new BollingerBands(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
    List<BigDecimal> middle = sma(prices, period);
    List<BigDecimal> upper = new ArrayList<>(prices.size());
    List<BigDecimal> lower = new ArrayList<>(prices.size());

    BigDecimal k = BigDecimal.valueOf(stdDevMultiplier);

    for (int i = 0; i < prices.size(); i++) {
      if (middle.get(i) == null || i < period - 1) {
        upper.add(null);
        lower.add(null);
        continue;
      }
      BigDecimal mean = middle.get(i);
      BigDecimal sumSq = BigDecimal.ZERO;
      for (int j = i - period + 1; j <= i; j++) {
        BigDecimal diff = prices.get(j).subtract(mean);
        sumSq = sumSq.add(diff.multiply(diff));
      }
      BigDecimal variance = sumSq.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
      BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(8, RoundingMode.HALF_UP);

      BigDecimal bandWidth = stdDev.multiply(k);
      upper.add(mean.add(bandWidth).setScale(4, RoundingMode.HALF_UP));
      lower.add(mean.subtract(bandWidth).setScale(4, RoundingMode.HALF_UP));
    }
    return new BollingerBands(upper, middle, lower);
  }

  public static MacdResult macd(List<BigDecimal> prices, int fastPeriod, int slowPeriod, int signalPeriod) {
    if (prices == null || prices.isEmpty() || fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
      return new MacdResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
    List<BigDecimal> fastEma = ema(prices, fastPeriod);
    List<BigDecimal> slowEma = ema(prices, slowPeriod);

    List<BigDecimal> macdLine = new ArrayList<>(prices.size());
    List<BigDecimal> validMacdValues = new ArrayList<>();

    for (int i = 0; i < prices.size(); i++) {
      if (fastEma.get(i) == null || slowEma.get(i) == null) {
        macdLine.add(null);
      } else {
        BigDecimal val = fastEma.get(i).subtract(slowEma.get(i)).setScale(8, RoundingMode.HALF_UP);
        macdLine.add(val);
        validMacdValues.add(val);
      }
    }

    List<BigDecimal> signalEma = ema(validMacdValues, signalPeriod);
    List<BigDecimal> signalLine = new ArrayList<>(prices.size());
    List<BigDecimal> histogram = new ArrayList<>(prices.size());

    int nullPrefixCount = prices.size() - validMacdValues.size();
    for (int i = 0; i < nullPrefixCount; i++) {
      signalLine.add(null);
      histogram.add(null);
    }

    for (int i = 0; i < validMacdValues.size(); i++) {
      BigDecimal sig = signalEma.get(i);
      signalLine.add(sig);
      if (sig != null && validMacdValues.get(i) != null) {
        histogram.add(validMacdValues.get(i).subtract(sig).setScale(4, RoundingMode.HALF_UP));
      } else {
        histogram.add(null);
      }
    }

    return new MacdResult(macdLine, signalLine, histogram);
  }

  private static BigDecimal calculateRsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
    if (avgGain.signum() == 0 && avgLoss.signum() == 0) {
      return BigDecimal.valueOf(50.00).setScale(4, RoundingMode.HALF_UP);
    }
    if (avgLoss.signum() == 0) {
      return BigDecimal.valueOf(100.00).setScale(4, RoundingMode.HALF_UP);
    }
    if (avgGain.signum() == 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
    BigDecimal onePlusRs = BigDecimal.ONE.add(rs);
    BigDecimal rsi = BigDecimal.valueOf(100.0).subtract(BigDecimal.valueOf(100.0).divide(onePlusRs, 8, RoundingMode.HALF_UP));
    return rsi.setScale(4, RoundingMode.HALF_UP);
  }
}
