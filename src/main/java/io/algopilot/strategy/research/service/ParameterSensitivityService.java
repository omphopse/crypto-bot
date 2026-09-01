package io.algopilot.strategy.research.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.ParameterSensitivityResult;
import io.algopilot.strategy.research.model.StrategyExperiment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ParameterSensitivityService {
  private final RealisticCostBacktestEngine backtestEngine;

  public ParameterSensitivityService(RealisticCostBacktestEngine backtestEngine) {
    this.backtestEngine = backtestEngine;
  }

  public List<ParameterSensitivityResult> evaluateSensitivity(StrategyExperiment exp, List<Candle> candles) {
    if (candles == null || candles.isEmpty()) {
      return Collections.emptyList();
    }

    List<ParameterSensitivityResult> results = new ArrayList<>();
    String[] rsiThresholds = {"25", "30", "35", "40"};

    for (String rsi : rsiThresholds) {
      RealisticCostBacktestEngine.SimulationOutput out = backtestEngine.runSimulation(exp, candles);

      String classification = "ROBUST";
      if (out.metrics().profitFactor().compareTo(new BigDecimal("1.2")) < 0) {
        classification = "FRAGILE";
      } else if (out.metrics().totalReturnPct().compareTo(new BigDecimal("80.0")) > 0) {
        classification = "OVERFIT";
      }

      results.add(new ParameterSensitivityResult(
          UUID.randomUUID(),
          exp.id(),
          "RSI_OVERSOLD_THRESHOLD",
          rsi,
          out.metrics().totalReturnPct(),
          out.metrics().netExpectancy(),
          out.metrics().profitFactor(),
          out.metrics().maxDrawdownPct(),
          classification
      ));
    }
    return results;
  }
}
