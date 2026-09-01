package io.algopilot.strategy.discovery.service;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.discovery.model.*;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateEvaluationService {
  private final CandidateStore candidateStore;
  private final RealisticCostBacktestEngine backtestEngine;
  private final CandidateStressService stressService;

  public CandidateEvaluationService(
      CandidateStore candidateStore,
      RealisticCostBacktestEngine backtestEngine,
      CandidateStressService stressService
  ) {
    this.candidateStore = candidateStore;
    this.backtestEngine = backtestEngine;
    this.stressService = stressService;
  }

  @Transactional
  public StrategyCandidate evaluateCandidate(UUID candidateId, List<Candle> candleData) {
    StrategyCandidate cand = candidateStore.findCandidateById(candidateId)
        .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

    List<Candle> candles = candleData;
    if (candles == null || candles.isEmpty()) {
      candles = generateSyntheticCandles(cand.symbol());
    }

    Instant now = Instant.now();
    StrategyExperiment exp = new StrategyExperiment(
        UUID.randomUUID(), cand.baseStrategyId(), UUID.randomUUID(), cand.symbol(), cand.timeframe(),
        now.minusSeconds(86400 * 30), now, new BigDecimal("10000.00"),
        SlippageModel.PERCENT, new BigDecimal("5.0"), new BigDecimal("10.0"),
        new BigDecimal("20.0"), new BigDecimal("0.50"), 50L, "DISCOVERY",
        ExperimentStatus.RUNNING, now
    );

    // 1. Run Backtest
    RealisticCostBacktestEngine.SimulationOutput simOutput = backtestEngine.runSimulation(exp, candles);

    // 2. Run Stress Tests
    List<StressResult> stressResults = stressService.runStressTests(candidateId, cand.symbol(), candles);

    // 3. Classify Robustness
    RobustnessTag classification = RobustnessTag.ROBUST;
    BigDecimal score = new BigDecimal("85.00");

    if (simOutput.trades().size() < 10) {
      classification = RobustnessTag.INSUFFICIENT_DATA;
      score = new BigDecimal("30.00");
    } else if (simOutput.metrics().netExpectancy().signum() <= 0) {
      classification = RobustnessTag.NEGATIVE_COST_EDGE;
      score = new BigDecimal("20.00");
    } else {
      long stressedProfitable = stressResults.stream().filter(StressResult::isProfitable).count();
      if (stressedProfitable < 2) {
        classification = RobustnessTag.FRAGILE;
        score = new BigDecimal("50.00");
      } else if (simOutput.metrics().totalReturnPct().compareTo(new BigDecimal("150.0")) > 0) {
        classification = RobustnessTag.OVERFIT;
        score = new BigDecimal("45.00");
      }
    }

    CandidateStatus nextStatus = (classification == RobustnessTag.ROBUST)
        ? CandidateStatus.PAPER_PENDING
        : CandidateStatus.REJECTED;

    StrategyCandidate updated = new StrategyCandidate(
        cand.candidateId(), cand.fingerprint(), cand.baseStrategyId(), cand.name(),
        cand.family(), cand.symbol(), cand.timeframe(), cand.parameters(),
        cand.generationMethod(), nextStatus, classification, score,
        simOutput.metrics().netExpectancy(), simOutput.metrics().profitFactor(),
        simOutput.metrics().maxDrawdownPct(), cand.createdAt()
    );

    return candidateStore.saveCandidate(updated);
  }

  private List<Candle> generateSyntheticCandles(String symbol) {
    List<Candle> list = new ArrayList<>();
    BigDecimal currentPrice = new BigDecimal("60000.00");
    Instant current = Instant.now().minusSeconds(200 * 3600L);

    for (int i = 0; i < 200; i++) {
      BigDecimal delta = BigDecimal.valueOf(Math.sin(i * 0.2) * 150.0 + (i % 4 == 0 ? 35 : -25));
      currentPrice = currentPrice.add(delta);
      list.add(new Candle(symbol, "1h", currentPrice, currentPrice.add(new BigDecimal("40.00")), currentPrice.subtract(new BigDecimal("40.00")), currentPrice, new BigDecimal("50.0"), current));
      current = current.plusSeconds(3600);
    }
    return list;
  }
}
