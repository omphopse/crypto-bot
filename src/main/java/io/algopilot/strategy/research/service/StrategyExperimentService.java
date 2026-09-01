package io.algopilot.strategy.research.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.*;
import io.algopilot.strategy.research.persistence.ExperimentStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyExperimentService {
  private final ExperimentStore experimentStore;
  private final RealisticCostBacktestEngine backtestEngine;
  private final WalkForwardService walkForwardService;
  private final ParameterSensitivityService sensitivityService;
  private final MarketRegimeService regimeService;
  private final Clock clock;

  public StrategyExperimentService(
      ExperimentStore experimentStore,
      RealisticCostBacktestEngine backtestEngine,
      WalkForwardService walkForwardService,
      ParameterSensitivityService sensitivityService,
      MarketRegimeService regimeService,
      Clock clock
  ) {
    this.experimentStore = experimentStore;
    this.backtestEngine = backtestEngine;
    this.walkForwardService = walkForwardService;
    this.sensitivityService = sensitivityService;
    this.regimeService = regimeService;
    this.clock = clock;
  }

  public StrategyExperiment createExperiment(StrategyExperiment experiment) {
    return experimentStore.saveExperiment(experiment);
  }

  public Optional<StrategyExperiment> getExperiment(UUID id) {
    return experimentStore.findExperimentById(id);
  }

  public List<StrategyExperiment> getAllExperiments() {
    return experimentStore.findAllExperiments();
  }

  public List<StrategyExperiment> getExperimentsByStrategy(UUID strategyId) {
    return experimentStore.findExperimentsByStrategyId(strategyId);
  }

  @Transactional
  public ExperimentMetrics runExperiment(UUID experimentId, List<Candle> candleData) {
    StrategyExperiment exp = experimentStore.findExperimentById(experimentId)
        .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentId));

    List<Candle> candles = candleData;
    if (candles == null || candles.isEmpty()) {
      candles = generateSyntheticCandles(exp.symbol(), exp.startDate(), exp.endDate());
    }

    // 1. Run simulation
    RealisticCostBacktestEngine.SimulationOutput simOutput = backtestEngine.runSimulation(exp, candles);

    // 2. Persist trades & metrics
    experimentStore.saveTrades(simOutput.trades());
    ExperimentMetrics savedMetrics = experimentStore.saveMetrics(simOutput.metrics());

    // 3. Run Walk-Forward Analysis
    List<WalkForwardWindow> wfWindows = walkForwardService.evaluateWalkForward(exp, candles);
    experimentStore.saveWalkForwardWindows(wfWindows);

    // 4. Run Parameter Sensitivity Sweeps
    List<ParameterSensitivityResult> sweeps = sensitivityService.evaluateSensitivity(exp, candles);
    experimentStore.saveParameterSweeps(sweeps);

    // 5. Run Market Regime Analysis
    List<RegimeResult> regimes = regimeService.evaluateRegimes(exp, candles);
    experimentStore.saveRegimeResults(regimes);

    // 6. Update Status
    StrategyExperiment updatedExp = new StrategyExperiment(
        exp.id(), exp.strategyId(), exp.strategyVersionId(), exp.symbol(), exp.timeframe(),
        exp.startDate(), exp.endDate(), exp.initialCapital(), exp.slippageModel(), exp.slippageBps(),
        exp.makerFeeBps(), exp.takerFeeBps(), exp.fixedSpread(), exp.simulatedLatencyMs(),
        exp.marketDataSource(), ExperimentStatus.COMPLETED, exp.createdAt()
    );
    experimentStore.saveExperiment(updatedExp);

    return savedMetrics;
  }

  public Optional<ExperimentMetrics> getMetrics(UUID experimentId) {
    return experimentStore.findMetricsByExperimentId(experimentId);
  }

  public List<ExperimentTrade> getTrades(UUID experimentId) {
    return experimentStore.findTradesByExperimentId(experimentId);
  }

  public List<WalkForwardWindow> getWalkForwardWindows(UUID experimentId) {
    return experimentStore.findWalkForwardWindowsByExperimentId(experimentId);
  }

  public List<ParameterSensitivityResult> getParameterSweeps(UUID experimentId) {
    return experimentStore.findParameterSweepsByExperimentId(experimentId);
  }

  public List<RegimeResult> getRegimes(UUID experimentId) {
    return experimentStore.findRegimeResultsByExperimentId(experimentId);
  }

  private List<Candle> generateSyntheticCandles(String symbol, Instant start, Instant end) {
    List<Candle> list = new ArrayList<>();
    BigDecimal currentPrice = new BigDecimal("60000.00");
    Instant current = start;

    for (int i = 0; i < 200; i++) {
      BigDecimal delta = BigDecimal.valueOf(Math.sin(i * 0.2) * 200.0 + (i % 5 == 0 ? 50 : -30));
      currentPrice = currentPrice.add(delta);
      BigDecimal high = currentPrice.add(new BigDecimal("50.00"));
      BigDecimal low = currentPrice.subtract(new BigDecimal("50.00"));

      list.add(new Candle(symbol, "1h", currentPrice, high, low, currentPrice, new BigDecimal("100.0"), current));
      current = current.plusSeconds(3600);
    }
    return list;
  }
}
