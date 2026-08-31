package io.algopilot.backtest.engine;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class IndicatorMathTest {

  @Test
  void testSma_calculatesMovingAverageAccurately() {
    List<BigDecimal> values = List.of(
        new BigDecimal("10.00"),
        new BigDecimal("20.00"),
        new BigDecimal("30.00"),
        new BigDecimal("40.00"),
        new BigDecimal("50.00")
    );

    List<BigDecimal> sma3 = Indicators.sma(values, 3);
    assertEquals(5, sma3.size());
    assertNull(sma3.get(0));
    assertNull(sma3.get(1));
    assertEquals(new BigDecimal("20.00000000"), sma3.get(2)); // (10 + 20 + 30) / 3 = 20
    assertEquals(new BigDecimal("30.00000000"), sma3.get(3)); // (20 + 30 + 40) / 3 = 30
    assertEquals(new BigDecimal("40.00000000"), sma3.get(4)); // (30 + 40 + 50) / 3 = 40
  }

  @Test
  void testEma_calculatesExponentialMovingAverage() {
    List<BigDecimal> values = List.of(
        new BigDecimal("22.00"),
        new BigDecimal("24.00"),
        new BigDecimal("26.00"),
        new BigDecimal("28.00"),
        new BigDecimal("30.00")
    );

    List<BigDecimal> ema3 = Indicators.ema(values, 3);
    assertEquals(5, ema3.size());
    assertNull(ema3.get(0));
    assertNull(ema3.get(1));
    // SMA of first 3 is (22 + 24 + 26) / 3 = 24.00
    assertEquals(new BigDecimal("24.00000000"), ema3.get(2));
    // Multiplier = 2 / (3 + 1) = 0.5. Next EMA = 28 * 0.5 + 24 * 0.5 = 26.00
    assertEquals(new BigDecimal("26.00000000"), ema3.get(3));
  }

  @Test
  void testRsi_boundsBetween0And100() {
    List<BigDecimal> prices = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      prices.add(BigDecimal.valueOf(100.0 + i * 2.0)); // Strictly increasing -> RSI should approach 100
    }

    List<BigDecimal> rsi = Indicators.rsi(prices, 14);
    assertEquals(30, rsi.size());
    assertNotNull(rsi.get(14));
    BigDecimal latestRsi = rsi.get(29);
    assertTrue(latestRsi.compareTo(BigDecimal.valueOf(90)) > 0);
    assertTrue(latestRsi.compareTo(BigDecimal.valueOf(100)) <= 0);
  }

  @Test
  void testAtr_calculatesAverageTrueRange() {
    List<Candle> candles = new ArrayList<>();
    Instant now = Instant.now();
    for (int i = 0; i < 20; i++) {
      candles.add(new Candle(
          "BTC/USD", "1h",
          BigDecimal.valueOf(100),
          BigDecimal.valueOf(110),
          BigDecimal.valueOf(90),
          BigDecimal.valueOf(105),
          BigDecimal.valueOf(1000),
          now.plusSeconds(i * 3600)
      ));
    }

    List<BigDecimal> atr = Indicators.atr(candles, 5);
    assertEquals(20, atr.size());
    assertNotNull(atr.get(4));
    assertEquals(new BigDecimal("20.00000000"), atr.get(4)); // Range is 110 - 90 = 20
  }
}
