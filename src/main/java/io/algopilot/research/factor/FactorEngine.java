package io.algopilot.research.factor;

import io.algopilot.backtest.engine.Indicators;
import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FactorEngine {

  public record FactorEvaluationResult(
      List<FactorScore> factors,
      BigDecimal compositeScore
  ) {}

  public FactorEvaluationResult evaluate(List<Candle> candles) {
    if (candles == null || candles.size() < 25) {
      return new FactorEvaluationResult(Collections.emptyList(), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
    }

    int n = candles.size();
    List<BigDecimal> closes = candles.stream().map(Candle::close).toList();
    List<BigDecimal> volumes = candles.stream().map(Candle::volume).toList();

    Candle latest = candles.get(n - 1);
    BigDecimal latestClose = latest.close();

    List<FactorScore> factors = new ArrayList<>();

    // 1. Momentum Factor
    List<BigDecimal> fastEma = Indicators.ema(closes, 9);
    List<BigDecimal> slowEma = Indicators.ema(closes, 21);
    BigDecimal fastVal = fastEma.get(n - 1);
    BigDecimal slowVal = slowEma.get(n - 1);

    BigDecimal momScore = BigDecimal.ZERO;
    BigDecimal momRaw = BigDecimal.ZERO;
    String momExpl = "Neutral momentum";

    if (fastVal != null && slowVal != null && slowVal.signum() > 0) {
      momRaw = fastVal.subtract(slowVal).divide(slowVal, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
      // Normalize: clamp between -1.0 and +1.0 (assuming +/- 2% diff is max)
      momScore = momRaw.divide(BigDecimal.valueOf(2.0), 4, RoundingMode.HALF_UP)
          .max(BigDecimal.valueOf(-1.0))
          .min(BigDecimal.valueOf(1.0));
      momExpl = momScore.signum() > 0 ? "Fast EMA above slow EMA indicates upward momentum" : "Fast EMA below slow EMA indicates downward momentum";
    }
    factors.add(new FactorScore("EMA_MOMENTUM", FactorType.MOMENTUM, momRaw.setScale(4, RoundingMode.HALF_UP), momScore.setScale(4, RoundingMode.HALF_UP), new BigDecimal("0.2500"), momExpl));

    // 2. Mean Reversion Factor (RSI based)
    List<BigDecimal> rsiList = Indicators.rsi(closes, 14);
    BigDecimal latestRsi = rsiList.get(n - 1);
    BigDecimal mrScore = BigDecimal.ZERO;
    BigDecimal mrRaw = latestRsi != null ? latestRsi : new BigDecimal("50.00");
    String mrExpl = "RSI in neutral range";

    if (latestRsi != null) {
      if (latestRsi.compareTo(BigDecimal.valueOf(30)) < 0) {
        // Oversold -> bullish mean reversion score
        mrScore = BigDecimal.valueOf(30).subtract(latestRsi).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        mrExpl = "RSI oversold (<30) indicates potential bullish bounce";
      } else if (latestRsi.compareTo(BigDecimal.valueOf(70)) > 0) {
        // Overbought -> bearish mean reversion score
        mrScore = BigDecimal.valueOf(70).subtract(latestRsi).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        mrExpl = "RSI overbought (>70) indicates potential bearish pullback";
      }
    }
    factors.add(new FactorScore("RSI_MEAN_REVERSION", FactorType.MEAN_REVERSION, mrRaw.setScale(4, RoundingMode.HALF_UP), mrScore.setScale(4, RoundingMode.HALF_UP), new BigDecimal("0.2000"), mrExpl));

    // 3. Volatility Breakout Factor
    List<BigDecimal> atrList = Indicators.atr(candles, 14);
    BigDecimal latestAtr = atrList.get(n - 1);
    BigDecimal range = latest.high().subtract(latest.low());
    BigDecimal vbScore = BigDecimal.ZERO;
    BigDecimal vbRaw = BigDecimal.ZERO;
    String vbExpl = "Normal candle volatility";

    if (latestAtr != null && latestAtr.signum() > 0) {
      vbRaw = range.divide(latestAtr, 4, RoundingMode.HALF_UP);
      if (vbRaw.compareTo(BigDecimal.valueOf(1.2)) > 0) {
        boolean green = latest.close().compareTo(latest.open()) >= 0;
        vbScore = green ? new BigDecimal("0.7500") : new BigDecimal("-0.7500");
        vbExpl = green ? "Bullish volatility expansion above 1.2x ATR" : "Bearish volatility expansion above 1.2x ATR";
      }
    }
    factors.add(new FactorScore("VOLATILITY_EXPANSION", FactorType.VOLATILITY_BREAKOUT, vbRaw.setScale(4, RoundingMode.HALF_UP), vbScore.setScale(4, RoundingMode.HALF_UP), new BigDecimal("0.2000"), vbExpl));

    // 4. Volume Imbalance Factor
    List<BigDecimal> avgVolList = Indicators.sma(volumes, 20);
    BigDecimal avgVol = avgVolList.get(n - 1);
    BigDecimal viScore = BigDecimal.ZERO;
    BigDecimal viRaw = latest.volume();
    String viExpl = "Average volume activity";

    if (avgVol != null && avgVol.signum() > 0) {
      BigDecimal volRatio = latest.volume().divide(avgVol, 4, RoundingMode.HALF_UP);
      if (volRatio.compareTo(BigDecimal.valueOf(1.5)) > 0) {
        boolean green = latest.close().compareTo(latest.open()) >= 0;
        viScore = green ? new BigDecimal("0.8000") : new BigDecimal("-0.8000");
        viExpl = green ? "High volume buying pressure (1.5x+ avg volume)" : "High volume selling pressure (1.5x+ avg volume)";
      }
    }
    factors.add(new FactorScore("VOLUME_SPIKE", FactorType.VOLUME_IMBALANCE, viRaw.setScale(4, RoundingMode.HALF_UP), viScore.setScale(4, RoundingMode.HALF_UP), new BigDecimal("0.1500"), viExpl));

    // 5. Trend Strength Factor
    List<BigDecimal> sma50List = Indicators.sma(closes, Math.min(closes.size(), 20));
    BigDecimal smaVal = sma50List.get(n - 1);
    BigDecimal tsScore = BigDecimal.ZERO;
    BigDecimal tsRaw = BigDecimal.ZERO;
    String tsExpl = "Price consolidates near moving average";

    if (smaVal != null && smaVal.signum() > 0) {
      tsRaw = latestClose.subtract(smaVal).divide(smaVal, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
      tsScore = tsRaw.divide(BigDecimal.valueOf(3.0), 4, RoundingMode.HALF_UP)
          .max(BigDecimal.valueOf(-1.0))
          .min(BigDecimal.valueOf(1.0));
      tsExpl = tsScore.signum() > 0 ? "Price trending above benchmark moving average" : "Price trending below benchmark moving average";
    }
    factors.add(new FactorScore("TREND_STRENGTH", FactorType.TREND_STRENGTH, tsRaw.setScale(4, RoundingMode.HALF_UP), tsScore.setScale(4, RoundingMode.HALF_UP), new BigDecimal("0.2000"), tsExpl));

    // Calculate Composite Alpha Score
    BigDecimal totalWeightedScore = BigDecimal.ZERO;
    BigDecimal totalWeight = BigDecimal.ZERO;

    for (FactorScore f : factors) {
      totalWeightedScore = totalWeightedScore.add(f.normalizedScore().multiply(f.weight()));
      totalWeight = totalWeight.add(f.weight());
    }

    BigDecimal compositeScore = totalWeight.signum() > 0
        ? totalWeightedScore.divide(totalWeight, 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    return new FactorEvaluationResult(factors, compositeScore);
  }
}
