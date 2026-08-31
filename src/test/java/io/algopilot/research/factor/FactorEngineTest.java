package io.algopilot.research.factor;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FactorEngineTest {
  private FactorEngine engine;

  @BeforeEach
  void setUp() {
    engine = new FactorEngine();
  }

  @Test
  void testEvaluate_generatesAllFiveFactorScoresAndBoundedCompositeScore() {
    List<Candle> candles = new ArrayList<>();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    BigDecimal price = new BigDecimal("50000.00");

    for (int i = 0; i < 50; i++) {
      double delta = 1.0 + Math.sin(i / 5.0) * 0.01;
      price = price.multiply(BigDecimal.valueOf(delta));
      candles.add(new Candle(
          "BTC/USD", "1h",
          price, price.multiply(BigDecimal.valueOf(1.002)), price.multiply(BigDecimal.valueOf(0.998)),
          price, BigDecimal.valueOf(100), now.plus(i, ChronoUnit.HOURS)
      ));
    }

    var result = engine.evaluate(candles);

    assertNotNull(result);
    assertEquals(5, result.factors().size());
    assertNotNull(result.compositeScore());
    assertTrue(result.compositeScore().compareTo(BigDecimal.valueOf(-1.0)) >= 0);
    assertTrue(result.compositeScore().compareTo(BigDecimal.valueOf(1.0)) <= 0);

    for (FactorScore f : result.factors()) {
      assertNotNull(f.factorName());
      assertNotNull(f.type());
      assertNotNull(f.normalizedScore());
      assertNotNull(f.weight());
      assertNotNull(f.explanation());
      assertTrue(f.normalizedScore().compareTo(BigDecimal.valueOf(-1.0)) >= 0);
      assertTrue(f.normalizedScore().compareTo(BigDecimal.valueOf(1.0)) <= 0);
    }
  }

  @Test
  void testEvaluate_insufficientCandles_returnsEmptyFactors() {
    var result = engine.evaluate(List.of());
    assertTrue(result.factors().isEmpty());
    assertEquals(BigDecimal.ZERO.setScale(4), result.compositeScore());
  }
}
