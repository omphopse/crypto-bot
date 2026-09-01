package io.algopilot.strategy.discovery.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.discovery.model.StressResult;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import io.algopilot.strategy.research.model.ExperimentStatus;
import io.algopilot.strategy.research.model.SlippageModel;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.service.RealisticCostBacktestEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CandidateStressService {
  private final CandidateStore candidateStore;
  private final RealisticCostBacktestEngine backtestEngine;

  public CandidateStressService(CandidateStore candidateStore, RealisticCostBacktestEngine backtestEngine) {
    this.candidateStore = candidateStore;
    this.backtestEngine = backtestEngine;
  }

  public List<StressResult> runStressTests(UUID candidateId, String symbol, List<Candle> candles) {
    List<StressResult> results = new ArrayList<>();
    Instant now = Instant.now();

    double[] costMultipliers = {1.0, 1.5, 2.0, 3.0};
    for (double mult : costMultipliers) {
      StrategyExperiment exp = new StrategyExperiment(
          UUID.randomUUID(), candidateId, UUID.randomUUID(), symbol, "1h",
          now.minusSeconds(86400 * 30), now, new BigDecimal("10000.00"),
          SlippageModel.PERCENT,
          BigDecimal.valueOf(5.0 * mult),
          BigDecimal.valueOf(10.0 * mult),
          BigDecimal.valueOf(20.0 * mult),
          BigDecimal.valueOf(0.50 * mult),
          50L,
          "STRESS_TEST",
          ExperimentStatus.RUNNING,
          now
      );

      RealisticCostBacktestEngine.SimulationOutput out = backtestEngine.runSimulation(exp, candles);
      boolean profitable = out.metrics().netPnl().signum() > 0;

      results.add(new StressResult(
          UUID.randomUUID(),
          candidateId,
          "COST_STRESS",
          BigDecimal.valueOf(mult),
          out.metrics().netPnl(),
          out.metrics().netExpectancy(),
          out.metrics().profitFactor(),
          profitable
      ));
    }

    candidateStore.saveStressResults(results);
    return results;
  }
}
