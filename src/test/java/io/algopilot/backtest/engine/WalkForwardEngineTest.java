package io.algopilot.backtest.engine;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardRequest;
import io.algopilot.backtest.model.WalkForwardResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WalkForwardEngineTest {
  private WalkForwardEngine engine;

  @BeforeEach
  void setUp() {
    engine = new WalkForwardEngine(new BacktestEngine());
  }

  @Test
  void testRun_executesRollingWindowsAndComputesEfficiency() {
    List<Candle> candles = new ArrayList<>();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    BigDecimal price = new BigDecimal("50000.00");

    for (int i = 0; i < 200; i++) {
      double delta = 1.0 + Math.sin(i / 5.0) * 0.01;
      price = price.multiply(BigDecimal.valueOf(delta));
      candles.add(new Candle(
          "BTC/USD", "1h",
          price, price.multiply(BigDecimal.valueOf(1.002)), price.multiply(BigDecimal.valueOf(0.998)),
          price, BigDecimal.valueOf(100), now.plus(i, ChronoUnit.HOURS)
      ));
    }

    WalkForwardRequest request = new WalkForwardRequest(
        UUID.randomUUID(), "BTC/USD", "1h", 4, 30, 15, new BigDecimal("100000.00")
    );

    WalkForwardResult result = engine.run(request, candles);

    assertNotNull(result);
    assertEquals(4, result.windowCount());
    assertEquals(4, result.windows().size());
    assertNotNull(result.avgOosEfficiency());
    for (var w : result.windows()) {
      assertNotNull(w.inSampleSharpe());
      assertNotNull(w.outOfSampleSharpe());
      assertNotNull(w.outOfSampleEfficiency());
    }
  }
}
