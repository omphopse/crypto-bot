package io.algopilot.strategy.research;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.backtest.model.Candle;
import io.algopilot.strategy.research.model.ExperimentMetrics;
import io.algopilot.strategy.research.model.ExperimentStatus;
import io.algopilot.strategy.research.model.SlippageModel;
import io.algopilot.strategy.research.model.StrategyExperiment;
import io.algopilot.strategy.research.service.RealisticCostBacktestEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealisticCostBacktestEngineTest {
  private RealisticCostBacktestEngine engine;
  private StrategyExperiment experiment;
  private Instant now;

  @BeforeEach
  void setUp() {
    engine = new RealisticCostBacktestEngine();
    now = Instant.parse("2026-09-02T12:00:00Z");

    experiment = new StrategyExperiment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BTC/USD",
        "1h",
        now.minusSeconds(86400 * 30),
        now,
        new BigDecimal("10000.00"),
        SlippageModel.PERCENT,
        new BigDecimal("5.0"), // 5 bps slippage
        new BigDecimal("10.0"), // 10 bps maker
        new BigDecimal("20.0"), // 20 bps taker
        new BigDecimal("0.50"), // $0.50 spread
        50L,
        "SYNTHETIC",
        ExperimentStatus.CREATED,
        now
    );
  }

  @Test
  void testSimulation_computesRealisticCostsAndExpectancy() {
    List<Candle> candles = generateCandles(100);

    RealisticCostBacktestEngine.SimulationOutput output = engine.runSimulation(experiment, candles);
    ExperimentMetrics metrics = output.metrics();

    assertThat(metrics).isNotNull();
    assertThat(metrics.totalFees()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(metrics.totalSlippage()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(metrics.totalSpreadCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(metrics.netExpectancy()).isNotNull();
    assertThat(metrics.economicNetResult()).isNotNull();
    assertThat(metrics.robustnessScore()).isNotNull();
  }

  private List<Candle> generateCandles(int count) {
    List<Candle> list = new ArrayList<>();
    BigDecimal currentPrice = new BigDecimal("60000.00");
    Instant current = now.minusSeconds(count * 3600L);

    for (int i = 0; i < count; i++) {
      BigDecimal delta = BigDecimal.valueOf(Math.sin(i * 0.3) * 150.0 + (i % 4 == 0 ? 40 : -20));
      currentPrice = currentPrice.add(delta);
      BigDecimal high = currentPrice.add(new BigDecimal("30.00"));
      BigDecimal low = currentPrice.subtract(new BigDecimal("30.00"));

      list.add(new Candle("BTC/USD", "1h", currentPrice, high, low, currentPrice, new BigDecimal("50.0"), current));
      current = current.plusSeconds(3600);
    }
    return list;
  }
}
