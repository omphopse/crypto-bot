package io.algopilot.strategy.research.service;

import io.algopilot.backtest.engine.Indicators;
import io.algopilot.backtest.engine.PerformanceMetricsCalculator;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.EquityPoint;
import io.algopilot.strategy.research.model.ExperimentMetrics;
import io.algopilot.strategy.research.model.ExperimentTrade;
import io.algopilot.strategy.research.model.StrategyExperiment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RealisticCostBacktestEngine {

  public record SimulationOutput(
      ExperimentMetrics metrics,
      List<ExperimentTrade> trades,
      List<EquityPoint> equityCurve
  ) {}

  public SimulationOutput runSimulation(StrategyExperiment exp, List<Candle> candles) {
    if (candles == null || candles.isEmpty()) {
      return emptyOutput(exp);
    }

    BigDecimal cash = exp.initialCapital();
    BigDecimal positionQty = BigDecimal.ZERO;
    BigDecimal positionEntryPrice = BigDecimal.ZERO;
    Instant positionEntryTime = null;
    String positionSide = null;

    BigDecimal slippageMultiplier = exp.slippageBps().divide(new BigDecimal("10000"), 8, RoundingMode.HALF_UP);
    BigDecimal takerFeeMultiplier = exp.takerFeeBps().divide(new BigDecimal("10000"), 8, RoundingMode.HALF_UP);
    BigDecimal halfSpread = exp.fixedSpread().divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);

    List<BigDecimal> closes = candles.stream().map(Candle::close).toList();
    List<BigDecimal> fastEma = Indicators.ema(closes, 9);
    List<BigDecimal> slowEma = Indicators.ema(closes, 21);
    List<BigDecimal> rsiList = Indicators.rsi(closes, 14);

    List<EquityPoint> equityCurve = new ArrayList<>();
    List<ExperimentTrade> trades = new ArrayList<>();
    List<BigDecimal> periodicReturns = new ArrayList<>();
    BigDecimal prevEquity = cash;

    BigDecimal totalFees = BigDecimal.ZERO;
    BigDecimal totalSlippage = BigDecimal.ZERO;
    BigDecimal totalSpread = BigDecimal.ZERO;
    long totalHoldingTimeMs = 0;

    for (int i = 0; i < candles.size(); i++) {
      Candle candle = candles.get(i);
      BigDecimal price = candle.close();

      BigDecimal currentFast = i < fastEma.size() ? fastEma.get(i) : null;
      BigDecimal currentSlow = i < slowEma.size() ? slowEma.get(i) : null;
      BigDecimal currentRsi = i < rsiList.size() ? rsiList.get(i) : null;

      // 1. Check exit signal if position is open
      if (positionQty.signum() != 0) {
        boolean exitSignal = false;
        String exitReason = "STRATEGY_EXIT";

        if ("BUY".equals(positionSide)) {
          if (currentFast != null && currentSlow != null && currentFast.compareTo(currentSlow) < 0) {
            exitSignal = true;
            exitReason = "EMA_BEARISH_CROSS";
          } else if (currentRsi != null && currentRsi.compareTo(BigDecimal.valueOf(75)) > 0) {
            exitSignal = true;
            exitReason = "RSI_OVERBOUGHT";
          }
        }

        if (exitSignal || i == candles.size() - 1) {
          BigDecimal exitSlippage = price.multiply(slippageMultiplier);
          BigDecimal effectiveExitPrice = price.subtract(exitSlippage).subtract(halfSpread);
          BigDecimal tradeValue = positionQty.multiply(effectiveExitPrice);
          BigDecimal fee = tradeValue.multiply(takerFeeMultiplier);
          BigDecimal spreadCost = positionQty.multiply(halfSpread.multiply(new BigDecimal("2")));
          BigDecimal slippageCost = positionQty.multiply(exitSlippage);

          BigDecimal grossPnl = positionQty.multiply(effectiveExitPrice.subtract(positionEntryPrice));
          BigDecimal netPnl = grossPnl.subtract(fee);

          cash = cash.add(tradeValue).subtract(fee);

          long holdingTime = candle.timestamp().toEpochMilli() - positionEntryTime.toEpochMilli();
          totalHoldingTimeMs += holdingTime;
          totalFees = totalFees.add(fee);
          totalSlippage = totalSlippage.add(slippageCost);
          totalSpread = totalSpread.add(spreadCost);

          trades.add(new ExperimentTrade(
              UUID.randomUUID(), exp.id(), candle.symbol(), positionSide,
              positionEntryTime, candle.timestamp(), positionEntryPrice, effectiveExitPrice,
              positionQty, grossPnl.setScale(4, RoundingMode.HALF_UP), netPnl.setScale(4, RoundingMode.HALF_UP),
              fee.setScale(4, RoundingMode.HALF_UP), slippageCost.setScale(4, RoundingMode.HALF_UP),
              spreadCost.setScale(4, RoundingMode.HALF_UP), exitReason
          ));

          positionQty = BigDecimal.ZERO;
          positionEntryPrice = BigDecimal.ZERO;
          positionSide = null;
        }
      }

      // 2. Check entry signal if flat
      if (positionQty.signum() == 0 && i < candles.size() - 1) {
        if (currentFast != null && currentSlow != null && currentFast.compareTo(currentSlow) > 0
            && currentRsi != null && currentRsi.compareTo(BigDecimal.valueOf(40)) > 0 && currentRsi.compareTo(BigDecimal.valueOf(70)) < 0) {
          BigDecimal entrySlippage = price.multiply(slippageMultiplier);
          BigDecimal effectiveEntryPrice = price.add(entrySlippage).add(halfSpread);

          BigDecimal capitalToDeploy = cash.multiply(new BigDecimal("0.90"));
          if (capitalToDeploy.compareTo(effectiveEntryPrice) > 0) {
            positionQty = capitalToDeploy.divide(effectiveEntryPrice, 4, RoundingMode.DOWN);
            BigDecimal entryValue = positionQty.multiply(effectiveEntryPrice);
            BigDecimal fee = entryValue.multiply(takerFeeMultiplier);

            cash = cash.subtract(entryValue).subtract(fee);
            positionEntryPrice = effectiveEntryPrice;
            positionEntryTime = candle.timestamp();
            positionSide = "BUY";
            totalFees = totalFees.add(fee);
          }
        }
      }

      BigDecimal currentEquity = cash.add(positionQty.multiply(price));
      equityCurve.add(new EquityPoint(candle.timestamp(), currentEquity.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO));

      if (prevEquity.signum() > 0) {
        periodicReturns.add(currentEquity.subtract(prevEquity).divide(prevEquity, 8, RoundingMode.HALF_UP));
      }
      prevEquity = currentEquity;
    }

    BigDecimal finalEquity = equityCurve.isEmpty() ? exp.initialCapital() : equityCurve.get(equityCurve.size() - 1).equity();
    BigDecimal netPnl = finalEquity.subtract(exp.initialCapital()).setScale(4, RoundingMode.HALF_UP);
    BigDecimal grossPnl = netPnl.add(totalFees).add(totalSlippage).add(totalSpread).setScale(4, RoundingMode.HALF_UP);

    BigDecimal totalReturnPct = exp.initialCapital().signum() > 0
        ? netPnl.divide(exp.initialCapital(), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    int totalTrades = trades.size();
    long wins = trades.stream().filter(t -> t.netPnl().signum() > 0).count();
    long losses = trades.stream().filter(t -> t.netPnl().signum() < 0).count();
    BigDecimal winRatePct = totalTrades > 0
        ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
        : BigDecimal.ZERO;

    BigDecimal avgWin = wins > 0
        ? trades.stream().filter(t -> t.netPnl().signum() > 0).map(ExperimentTrade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    BigDecimal avgLoss = losses > 0
        ? trades.stream().filter(t -> t.netPnl().signum() < 0).map(t -> t.netPnl().abs()).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    BigDecimal winRatio = totalTrades > 0 ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(totalTrades), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal lossRatio = totalTrades > 0 ? BigDecimal.valueOf(losses).divide(BigDecimal.valueOf(totalTrades), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal grossExpectancy = winRatio.multiply(avgWin).subtract(lossRatio.multiply(avgLoss)).setScale(6, RoundingMode.HALF_UP);

    BigDecimal avgCostPerTrade = totalTrades > 0
        ? totalFees.add(totalSlippage).add(totalSpread).divide(BigDecimal.valueOf(totalTrades), 6, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    BigDecimal netExpectancy = grossExpectancy.subtract(avgCostPerTrade).setScale(6, RoundingMode.HALF_UP);

    BigDecimal maxDrawdownPct = PerformanceMetricsCalculator.calculateMaxDrawdownPct(equityCurve);
    BigDecimal sharpeRatio = PerformanceMetricsCalculator.calculateSharpeRatio(periodicReturns, BigDecimal.valueOf(0.04));
    BigDecimal profitFactor = losses > 0 && avgLoss.signum() > 0
        ? avgWin.multiply(BigDecimal.valueOf(wins)).divide(avgLoss.multiply(BigDecimal.valueOf(losses)), 4, RoundingMode.HALF_UP)
        : BigDecimal.ONE;

    long avgHoldingMs = totalTrades > 0 ? totalHoldingTimeMs / totalTrades : 0L;

    // Economic Net Result (deducting estimated AI & infra cost: ~$0.50 per 10 trades)
    BigDecimal estimatedAiInfraCost = BigDecimal.valueOf(totalTrades).multiply(new BigDecimal("0.05"));
    BigDecimal economicNetResult = netPnl.subtract(estimatedAiInfraCost).setScale(4, RoundingMode.HALF_UP);

    // Robustness scoring & Overfitting Warnings
    List<String> warnings = new ArrayList<>();
    if (totalTrades < 30) {
      warnings.add("LOW_TRADE_COUNT: Less than 30 trades in sample (" + totalTrades + ")");
    }
    if (grossExpectancy.compareTo(avgCostPerTrade) <= 0) {
      warnings.add("NEGATIVE_COST_EDGE: Average trading cost per trade exceeds gross edge");
    }
    if (totalReturnPct.compareTo(new BigDecimal("100.0")) > 0) {
      warnings.add("UNREALISTIC_HIGH_RETURN: Return exceeds 100%, verify against curve overfitting");
    }

    BigDecimal robustnessScore = new BigDecimal("75.00");
    if (!warnings.isEmpty()) {
      robustnessScore = robustnessScore.subtract(BigDecimal.valueOf(warnings.size() * 15L));
    }
    if (robustnessScore.compareTo(BigDecimal.ZERO) < 0) robustnessScore = BigDecimal.ZERO;

    ExperimentMetrics metrics = new ExperimentMetrics(
        exp.id(), grossPnl, netPnl, totalReturnPct, BigDecimal.ZERO, winRatePct, avgWin, avgLoss,
        profitFactor, grossExpectancy, netExpectancy, maxDrawdownPct, sharpeRatio, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, totalTrades, avgHoldingMs, totalFees, totalSlippage,
        totalSpread, BigDecimal.ZERO, economicNetResult, robustnessScore, warnings
    );

    return new SimulationOutput(metrics, trades, equityCurve);
  }

  private SimulationOutput emptyOutput(StrategyExperiment exp) {
    ExperimentMetrics metrics = new ExperimentMetrics(
        exp.id(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0L,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, List.of("INSUFFICIENT_DATA")
    );
    return new SimulationOutput(metrics, Collections.emptyList(), Collections.emptyList());
  }
}
