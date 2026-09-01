package io.algopilot.strategy.research.persistence;

import io.algopilot.strategy.research.model.ExperimentMetrics;
import io.algopilot.strategy.research.model.ExperimentTrade;
import io.algopilot.strategy.research.model.ParameterSensitivityResult;
import io.algopilot.strategy.research.model.RegimeResult;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.model.StrategyHealthMetric;
import io.algopilot.strategy.research.model.WalkForwardWindow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentStore {
  StrategyExperiment saveExperiment(StrategyExperiment experiment);
  Optional<StrategyExperiment> findExperimentById(UUID id);
  List<StrategyExperiment> findAllExperiments();
  List<StrategyExperiment> findExperimentsByStrategyId(UUID strategyId);

  ExperimentMetrics saveMetrics(ExperimentMetrics metrics);
  Optional<ExperimentMetrics> findMetricsByExperimentId(UUID experimentId);

  void saveTrades(List<ExperimentTrade> trades);
  List<ExperimentTrade> findTradesByExperimentId(UUID experimentId);

  void saveWalkForwardWindows(List<WalkForwardWindow> windows);
  List<WalkForwardWindow> findWalkForwardWindowsByExperimentId(UUID experimentId);

  void saveParameterSweeps(List<ParameterSensitivityResult> sweeps);
  List<ParameterSensitivityResult> findParameterSweepsByExperimentId(UUID experimentId);

  void saveRegimeResults(List<RegimeResult> regimes);
  List<RegimeResult> findRegimeResultsByExperimentId(UUID experimentId);

  StrategyHealthMetric saveStrategyHealth(StrategyHealthMetric health);
  Optional<StrategyHealthMetric> findLatestStrategyHealth(UUID strategyId);
}
