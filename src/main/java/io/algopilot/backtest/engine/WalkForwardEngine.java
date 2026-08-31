package io.algopilot.backtest.engine;

import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardRequest;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.model.WalkForwardWindowResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WalkForwardEngine {
  private final BacktestEngine backtestEngine;
  private final Clock clock;

  public WalkForwardEngine(BacktestEngine backtestEngine) {
    this(backtestEngine, Clock.systemUTC());
  }

  public WalkForwardEngine(BacktestEngine backtestEngine, Clock clock) {
    this.backtestEngine = backtestEngine;
    this.clock = clock;
  }

  public WalkForwardResult run(WalkForwardRequest request, List<Candle> candles) {
    int windowCount = Math.max(1, request.windowCount());
    if (candles == null || candles.size() < windowCount * 10) {
      return new WalkForwardResult(
          UUID.randomUUID(), request.strategyVersionId(), request.symbol(), request.timeframe(),
          windowCount, BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), Collections.emptyList(), clock.instant()
      );
    }

    int totalCandles = candles.size();
    int windowSize = totalCandles / windowCount;
    int isSize = (int) (windowSize * 0.70); // 70% In-Sample
    int oosSize = windowSize - isSize;     // 30% Out-Of-Sample

    List<WalkForwardWindowResult> windows = new ArrayList<>();
    BigDecimal totalEfficiency = BigDecimal.ZERO;
    int validEfficiencyCount = 0;

    for (int w = 0; w < windowCount; w++) {
      int windowStart = w * windowSize;
      int isEnd = windowStart + isSize;
      int oosEnd = Math.min(totalCandles, windowStart + windowSize);

      List<Candle> isCandles = candles.subList(windowStart, isEnd);
      List<Candle> oosCandles = candles.subList(isEnd, oosEnd);

      if (isCandles.isEmpty() || oosCandles.isEmpty()) {
        continue;
      }

      BacktestRequest isReq = new BacktestRequest(
          request.strategyVersionId(), request.symbol(), request.timeframe(),
          isCandles.get(0).timestamp(), isCandles.get(isCandles.size() - 1).timestamp(),
          request.initialCapital(), 5, 10
      );
      BacktestResult isRes = backtestEngine.run(isReq, isCandles);

      BacktestRequest oosReq = new BacktestRequest(
          request.strategyVersionId(), request.symbol(), request.timeframe(),
          oosCandles.get(0).timestamp(), oosCandles.get(oosCandles.size() - 1).timestamp(),
          request.initialCapital(), 5, 10
      );
      BacktestResult oosRes = backtestEngine.run(oosReq, oosCandles);

      BigDecimal isSharpe = isRes.sharpeRatio();
      BigDecimal oosSharpe = oosRes.sharpeRatio();

      BigDecimal efficiency = BigDecimal.ONE;
      if (isSharpe.signum() > 0) {
        efficiency = oosSharpe.divide(isSharpe, 4, RoundingMode.HALF_UP);
      } else if (isSharpe.signum() <= 0 && oosSharpe.signum() <= 0) {
        efficiency = BigDecimal.ZERO;
      }

      totalEfficiency = totalEfficiency.add(efficiency);
      validEfficiencyCount++;

      windows.add(new WalkForwardWindowResult(
          w + 1,
          isCandles.get(0).timestamp(), isCandles.get(isCandles.size() - 1).timestamp(),
          oosCandles.get(0).timestamp(), oosCandles.get(oosCandles.size() - 1).timestamp(),
          isSharpe, oosSharpe, efficiency,
          Map.of("fastEma", 9, "slowEma", 21, "rsiPeriod", 14)
      ));
    }

    BigDecimal avgEfficiency = validEfficiencyCount > 0
        ? totalEfficiency.divide(BigDecimal.valueOf(validEfficiencyCount), 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    return new WalkForwardResult(
        UUID.randomUUID(), request.strategyVersionId(), request.symbol(), request.timeframe(),
        windowCount, avgEfficiency, windows, clock.instant()
    );
  }
}
