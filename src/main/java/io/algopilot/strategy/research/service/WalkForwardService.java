package io.algopilot.strategy.research.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.model.WalkForwardWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardService {
  private final RealisticCostBacktestEngine backtestEngine;

  public WalkForwardService(RealisticCostBacktestEngine backtestEngine) {
    this.backtestEngine = backtestEngine;
  }

  public List<WalkForwardWindow> evaluateWalkForward(StrategyExperiment exp, List<Candle> candles) {
    if (candles == null || candles.size() < 100) {
      return Collections.emptyList();
    }

    List<WalkForwardWindow> windows = new ArrayList<>();
    int windowCount = 3;
    int chunkSize = candles.size() / (windowCount + 1);

    for (int w = 0; w < windowCount; w++) {
      int isStartIdx = w * chunkSize;
      int isEndIdx = isStartIdx + (int) (chunkSize * 1.5);
      int oosStartIdx = isEndIdx;
      int oosEndIdx = Math.min(candles.size() - 1, oosStartIdx + (int) (chunkSize * 0.5));

      if (oosEndIdx <= oosStartIdx || isEndIdx <= isStartIdx) continue;

      List<Candle> inSampleCandles = candles.subList(isStartIdx, isEndIdx);
      List<Candle> outOfSampleCandles = candles.subList(oosStartIdx, oosEndIdx + 1);

      RealisticCostBacktestEngine.SimulationOutput isOutput = backtestEngine.runSimulation(exp, inSampleCandles);
      RealisticCostBacktestEngine.SimulationOutput oosOutput = backtestEngine.runSimulation(exp, outOfSampleCandles);

      BigDecimal isReturn = isOutput.metrics().totalReturnPct();
      BigDecimal oosReturn = oosOutput.metrics().totalReturnPct();

      BigDecimal degradationRatio = isReturn.signum() != 0
          ? oosReturn.divide(isReturn.abs(), 4, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;

      windows.add(new WalkForwardWindow(
          UUID.randomUUID(),
          exp.id(),
          w + 1,
          inSampleCandles.get(0).timestamp(),
          inSampleCandles.get(inSampleCandles.size() - 1).timestamp(),
          outOfSampleCandles.get(0).timestamp(),
          outOfSampleCandles.get(outOfSampleCandles.size() - 1).timestamp(),
          isReturn,
          oosReturn,
          degradationRatio
      ));
    }
    return windows;
  }
}
