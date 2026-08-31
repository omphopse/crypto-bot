package io.algopilot.backtest.engine;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BacktestEngineTest {
  private BacktestEngine engine;

  @BeforeEach
  void setUp() {
    engine = new BacktestEngine();
  }

  @Test
  void testRun_deterministicSimulationReturnsValidResults() {
    List<Candle> candles = new ArrayList<>();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    BigDecimal price = new BigDecimal("50000.00");

    // Create 100 upward trending candles followed by mean reversion
    for (int i = 0; i < 100; i++) {
      double delta = (i < 50) ? 1.01 : 0.99;
      price = price.multiply(BigDecimal.valueOf(delta));
      candles.add(new Candle(
          "BTC/USD", "1h",
          price, price.multiply(BigDecimal.valueOf(1.005)), price.multiply(BigDecimal.valueOf(0.995)),
          price, BigDecimal.valueOf(100), now.plus(i, ChronoUnit.HOURS)
      ));
    }

    BacktestRequest request = new BacktestRequest(
        UUID.randomUUID(), "BTC/USD", "1h",
        candles.get(0).timestamp(), candles.get(candles.size() - 1).timestamp(),
        new BigDecimal("100000.00"), 5, 10
    );

    BacktestResult result = engine.run(request, candles);

    assertNotNull(result);
    assertEquals("BTC/USD", result.symbol());
    assertEquals(new BigDecimal("100000.00"), result.initialCapital());
    assertNotNull(result.finalEquity());
    assertNotNull(result.totalReturnPct());
    assertNotNull(result.maxDrawdownPct());
    assertNotNull(result.sharpeRatio());
    assertFalse(result.equityCurve().isEmpty());
  }

  @Test
  void testRun_emptyCandles_returnsZeroMetrics() {
    BacktestRequest request = new BacktestRequest(
        UUID.randomUUID(), "BTC/USD", "1h",
        Instant.now(), Instant.now(),
        new BigDecimal("100000.00"), 5, 10
    );

    BacktestResult result = engine.run(request, List.of());
    assertEquals(0, result.totalTrades());
    assertEquals(new BigDecimal("100000.00"), result.finalEquity());
    assertEquals(BigDecimal.ZERO.setScale(4), result.totalReturnPct());
  }
}
