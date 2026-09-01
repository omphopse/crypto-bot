package io.algopilot.backtest.engine;

import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.EquityPoint;
import io.algopilot.backtest.model.SimulatedTrade;
import io.algopilot.risk.RiskDecisionRequest.Side;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BacktestEngine {
  private final Clock clock;

  public BacktestEngine() {
    this(Clock.systemUTC());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BacktestEngine(@org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public BacktestResult run(BacktestRequest request, List<Candle> candles) {
    if (candles == null || candles.isEmpty()) {
      return emptyResult(request);
    }

    UUID backtestId = UUID.randomUUID();
    BigDecimal cash = request.initialCapital();
    BigDecimal positionQty = BigDecimal.ZERO;
    BigDecimal positionEntryPrice = BigDecimal.ZERO;
    Instant positionEntryTime = null;
    Side positionSide = null;

    int slippageBps = request.slippageBps() != null ? request.slippageBps() : 5;
    int feeBps = request.feeBps() != null ? request.feeBps() : 10;
    BigDecimal slippageMultiplier = BigDecimal.valueOf(slippageBps).divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
    BigDecimal feeMultiplier = BigDecimal.valueOf(feeBps).divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);

    List<BigDecimal> closes = candles.stream().map(Candle::close).toList();
    List<BigDecimal> fastEma = Indicators.ema(closes, 9);
    List<BigDecimal> slowEma = Indicators.ema(closes, 21);
    List<BigDecimal> rsiList = Indicators.rsi(closes, 14);

    List<EquityPoint> equityCurve = new ArrayList<>();
    List<SimulatedTrade> trades = new ArrayList<>();
    List<BigDecimal> periodicReturns = new ArrayList<>();
    BigDecimal prevEquity = cash;

    for (int i = 0; i < candles.size(); i++) {
      Candle candle = candles.get(i);
      BigDecimal price = candle.close();

      BigDecimal currentFast = i < fastEma.size() ? fastEma.get(i) : null;
      BigDecimal currentSlow = i < slowEma.size() ? slowEma.get(i) : null;
      BigDecimal currentRsi = i < rsiList.size() ? rsiList.get(i) : null;

      // Check exit signal if position is open
      if (positionQty.signum() != 0) {
        boolean exitSignal = false;
        String exitReason = "STRATEGY_EXIT";

        if (positionSide == Side.BUY) {
          // Exit long if fast EMA crosses below slow EMA or RSI > 75 (overbought)
          if (currentFast != null && currentSlow != null && currentFast.compareTo(currentSlow) < 0) {
            exitSignal = true;
            exitReason = "EMA_BEARISH_CROSS";
          } else if (currentRsi != null && currentRsi.compareTo(BigDecimal.valueOf(75)) > 0) {
            exitSignal = true;
            exitReason = "RSI_OVERBOUGHT";
          }
        }

        if (exitSignal || i == candles.size() - 1) { // Close position at end of backtest
          BigDecimal exitSlippage = price.multiply(slippageMultiplier);
          BigDecimal effectiveExitPrice = price.subtract(exitSlippage);
          BigDecimal tradeValue = positionQty.multiply(effectiveExitPrice);
          BigDecimal fee = tradeValue.multiply(feeMultiplier);

          BigDecimal grossPnl = positionQty.multiply(effectiveExitPrice.subtract(positionEntryPrice));
          BigDecimal netPnl = grossPnl.subtract(fee);
          BigDecimal returnPct = effectiveExitPrice.subtract(positionEntryPrice).divide(positionEntryPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

          cash = cash.add(tradeValue).subtract(fee);

          trades.add(new SimulatedTrade(
              UUID.randomUUID(), backtestId, candle.symbol(), positionSide,
              positionEntryTime, candle.timestamp(), positionEntryPrice, effectiveExitPrice,
              positionQty, netPnl.setScale(4, RoundingMode.HALF_UP), fee.setScale(4, RoundingMode.HALF_UP),
              returnPct.setScale(4, RoundingMode.HALF_UP), exitReason
          ));

          positionQty = BigDecimal.ZERO;
          positionEntryPrice = BigDecimal.ZERO;
          positionSide = null;
        }
      }

      // Check entry signal if position is flat
      if (positionQty.signum() == 0 && i < candles.size() - 1) {
        if (currentFast != null && currentSlow != null && currentFast.compareTo(currentSlow) > 0
            && currentRsi != null && currentRsi.compareTo(BigDecimal.valueOf(40)) > 0 && currentRsi.compareTo(BigDecimal.valueOf(70)) < 0) {
          // Bullish entry
          BigDecimal entrySlippage = price.multiply(slippageMultiplier);
          BigDecimal effectiveEntryPrice = price.add(entrySlippage);

          // Use up to 90% of available cash
          BigDecimal capitalToDeploy = cash.multiply(BigDecimal.valueOf(0.90));
          if (capitalToDeploy.compareTo(effectiveEntryPrice) > 0) {
            positionQty = capitalToDeploy.divide(effectiveEntryPrice, 4, RoundingMode.DOWN);
            BigDecimal entryValue = positionQty.multiply(effectiveEntryPrice);
            BigDecimal fee = entryValue.multiply(feeMultiplier);

            cash = cash.subtract(entryValue).subtract(fee);
            positionEntryPrice = effectiveEntryPrice;
            positionEntryTime = candle.timestamp();
            positionSide = Side.BUY;
          }
        }
      }

      // Mark to market current equity
      BigDecimal currentPositionValue = positionQty.multiply(price);
      BigDecimal currentEquity = cash.add(currentPositionValue);
      equityCurve.add(new EquityPoint(candle.timestamp(), currentEquity.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO));

      if (prevEquity.signum() > 0) {
        BigDecimal ret = currentEquity.subtract(prevEquity).divide(prevEquity, 8, RoundingMode.HALF_UP);
        periodicReturns.add(ret);
      }
      prevEquity = currentEquity;
    }

    BigDecimal finalEquity = equityCurve.isEmpty() ? request.initialCapital() : equityCurve.get(equityCurve.size() - 1).equity();
    BigDecimal totalReturnPct = finalEquity.subtract(request.initialCapital())
        .divide(request.initialCapital(), 6, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(4, RoundingMode.HALF_UP);

    BigDecimal maxDrawdownPct = PerformanceMetricsCalculator.calculateMaxDrawdownPct(equityCurve);
    BigDecimal sharpeRatio = PerformanceMetricsCalculator.calculateSharpeRatio(periodicReturns, BigDecimal.valueOf(0.04));
    BigDecimal profitFactor = PerformanceMetricsCalculator.calculateProfitFactor(trades);
    BigDecimal winRate = PerformanceMetricsCalculator.calculateWinRate(trades);

    int totalTrades = trades.size();
    int winningTrades = (int) trades.stream().filter(t -> t.pnl().signum() > 0).count();
    int losingTrades = (int) trades.stream().filter(t -> t.pnl().signum() < 0).count();

    return new BacktestResult(
        backtestId, request.strategyVersionId(), request.symbol(), request.timeframe(),
        candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
        request.initialCapital(), finalEquity, totalReturnPct, totalTrades, winningTrades, losingTrades,
        winRate, maxDrawdownPct, sharpeRatio, profitFactor, equityCurve, trades, clock.instant()
    );
  }

  private BacktestResult emptyResult(BacktestRequest request) {
    return new BacktestResult(
        UUID.randomUUID(), request.strategyVersionId(), request.symbol(), request.timeframe(),
        request.startTime(), request.endTime(), request.initialCapital(), request.initialCapital(),
        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), 0, 0, 0,
        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
        BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
        Collections.emptyList(), Collections.emptyList(), clock.instant()
    );
  }
}
