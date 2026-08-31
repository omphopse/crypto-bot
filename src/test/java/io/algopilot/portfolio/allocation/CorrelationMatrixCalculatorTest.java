package io.algopilot.portfolio.allocation;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CorrelationMatrixCalculatorTest {

  @Test
  void testCalculateReturns_andCorrelation() {
    Instant now = Instant.now();
    List<Candle> seriesA = List.of(
        new Candle("A", "1h", new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ONE, now),
        new Candle("A", "1h", new BigDecimal("105"), new BigDecimal("105"), new BigDecimal("105"), new BigDecimal("105"), BigDecimal.ONE, now.plusSeconds(3600)),
        new Candle("A", "1h", new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("110"), BigDecimal.ONE, now.plusSeconds(7200))
    );

    List<Candle> seriesB = List.of(
        new Candle("B", "1h", new BigDecimal("50"), new BigDecimal("50"), new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ONE, now),
        new Candle("B", "1h", new BigDecimal("52.5"), new BigDecimal("52.5"), new BigDecimal("52.5"), new BigDecimal("52.5"), BigDecimal.ONE, now.plusSeconds(3600)),
        new Candle("B", "1h", new BigDecimal("55"), new BigDecimal("55"), new BigDecimal("55"), new BigDecimal("55"), BigDecimal.ONE, now.plusSeconds(7200))
    );

    List<BigDecimal> retA = CorrelationMatrixCalculator.calculateReturns(seriesA);
    List<BigDecimal> retB = CorrelationMatrixCalculator.calculateReturns(seriesB);

    assertEquals(2, retA.size());
    assertEquals(2, retB.size());

    // Series A and B move in identical positive lockstep -> correlation should be +1.0000
    BigDecimal corr = CorrelationMatrixCalculator.calculateCorrelation(retA, retB);
    assertEquals(new BigDecimal("1.0000"), corr);
  }
}
