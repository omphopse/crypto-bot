package io.algopilot.strategy.research;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.ExperimentStatus;
import io.algopilot.strategy.research.model.ParameterSensitivityResult;
import io.algopilot.strategy.research.model.SlippageModel;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.service.ParameterSensitivityService;
import io.algopilot.strategy.research.service.RealisticCostBacktestEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterSensitivityServiceTest {
  private ParameterSensitivityService sensitivityService;
  private StrategyExperiment experiment;
  private Instant now;

  @BeforeEach
  void setUp() {
    RealisticCostBacktestEngine engine = new RealisticCostBacktestEngine();
    sensitivityService = new ParameterSensitivityService(engine);
    now = Instant.parse("2026-09-02T12:00:00Z");

    experiment = new StrategyExperiment(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BTC/USD", "1h",
        now.minusSeconds(86400 * 30), now, new BigDecimal("10000.00"),
        SlippageModel.PERCENT, new BigDecimal("5.0"), new BigDecimal("10.0"),
        new BigDecimal("20.0"), new BigDecimal("0.50"), 50L, "SYNTHETIC",
        ExperimentStatus.CREATED, now
    );
  }

  @Test
  void testEvaluateSensitivity_classifiesParameterRobustness() {
    List<Candle> candles = generateCandles(80);

    List<ParameterSensitivityResult> results = sensitivityService.evaluateSensitivity(experiment, candles);

    assertThat(results).hasSize(4);
    for (ParameterSensitivityResult r : results) {
      assertThat(r.robustnessClassification()).isIn("ROBUST", "FRAGILE", "OVERFIT");
      assertThat(r.netExpectancy()).isNotNull();
    }
  }

  private List<Candle> generateCandles(int count) {
    List<Candle> list = new ArrayList<>();
    BigDecimal currentPrice = new BigDecimal("60000.00");
    Instant current = now.minusSeconds(count * 3600L);

    for (int i = 0; i < count; i++) {
      BigDecimal delta = BigDecimal.valueOf(Math.sin(i * 0.4) * 80.0);
      currentPrice = currentPrice.add(delta);
      list.add(new Candle("BTC/USD", "1h", currentPrice, currentPrice, currentPrice, currentPrice, BigDecimal.TEN, current));
      current = current.plusSeconds(3600);
    }
    return list;
  }
}
