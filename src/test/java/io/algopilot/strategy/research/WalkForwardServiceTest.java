package io.algopilot.strategy.research;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.ExperimentStatus;
import io.algopilot.strategy.research.model.SlippageModel;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.model.WalkForwardWindow;
import io.algopilot.strategy.research.service.RealisticCostBacktestEngine;
import io.algopilot.strategy.research.service.WalkForwardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalkForwardServiceTest {
  private WalkForwardService walkForwardService;
  private StrategyExperiment experiment;
  private Instant now;

  @BeforeEach
  void setUp() {
    RealisticCostBacktestEngine engine = new RealisticCostBacktestEngine();
    walkForwardService = new WalkForwardService(engine);
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
  void testEvaluateWalkForward_producesRollingWindowsWithDegradationRatios() {
    List<Candle> candles = generateCandles(150);

    List<WalkForwardWindow> windows = walkForwardService.evaluateWalkForward(experiment, candles);

    assertThat(windows).isNotEmpty();
    for (WalkForwardWindow w : windows) {
      assertThat(w.inSampleNetReturnPct()).isNotNull();
      assertThat(w.outOfSampleNetReturnPct()).isNotNull();
      assertThat(w.oosDegradationRatio()).isNotNull();
    }
  }

  private List<Candle> generateCandles(int count) {
    List<Candle> list = new ArrayList<>();
    BigDecimal currentPrice = new BigDecimal("60000.00");
    Instant current = now.minusSeconds(count * 3600L);

    for (int i = 0; i < count; i++) {
      BigDecimal delta = BigDecimal.valueOf(Math.sin(i * 0.3) * 100.0);
      currentPrice = currentPrice.add(delta);
      list.add(new Candle("BTC/USD", "1h", currentPrice, currentPrice, currentPrice, currentPrice, BigDecimal.TEN, current));
      current = current.plusSeconds(3600);
    }
    return list;
  }
}
