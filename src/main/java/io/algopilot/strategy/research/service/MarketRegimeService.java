package io.algopilot.strategy.research.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.RegimeResult;
import io.algopilot.strategy.research.model.StrategyExperiment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MarketRegimeService {
  private final RealisticCostBacktestEngine backtestEngine;

  public MarketRegimeService(RealisticCostBacktestEngine backtestEngine) {
    this.backtestEngine = backtestEngine;
  }

  public List<RegimeResult> evaluateRegimes(StrategyExperiment exp, List<Candle> candles) {
    if (candles == null || candles.size() < 40) {
      return Collections.emptyList();
    }

    List<RegimeResult> list = new ArrayList<>();
    String[] regimes = {"BULL_TREND", "BEAR_TREND", "SIDEWAYS", "HIGH_VOLATILITY", "LOW_VOLATILITY"};

    int slice = candles.size() / regimes.length;
    for (int i = 0; i < regimes.length; i++) {
      int start = i * slice;
      int end = (i == regimes.length - 1) ? candles.size() : (i + 1) * slice;
      List<Candle> subset = candles.subList(start, end);

      RealisticCostBacktestEngine.SimulationOutput out = backtestEngine.runSimulation(exp, subset);

      list.add(new RegimeResult(
          UUID.randomUUID(),
          exp.id(),
          regimes[i],
          out.trades().size(),
          out.metrics().netPnl(),
          out.metrics().winRatePct(),
          out.metrics().profitFactor(),
          out.metrics().netExpectancy(),
          out.metrics().maxDrawdownPct()
      ));
    }
    return list;
  }
}
