package io.algopilot.portfolio.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PortfolioRiskCalculatorTest {

  @Test
  void testCalculatePortfolioRiskMetrics() {
    Map<String, BigDecimal> weights = Map.of("A", new BigDecimal("0.50"), "B", new BigDecimal("0.50"));
    Map<String, BigDecimal> vols = Map.of("A", new BigDecimal("0.02"), "B", new BigDecimal("0.02"));
    Map<String, Map<String, BigDecimal>> corr = Map.of(
        "A", Map.of("A", BigDecimal.ONE, "B", BigDecimal.ZERO),
        "B", Map.of("A", BigDecimal.ZERO, "B", BigDecimal.ONE)
    );

    BigDecimal portVol = PortfolioRiskCalculator.calculatePortfolioVolatility(weights, vols, corr);
    assertNotNull(portVol);
    assertTrue(portVol.signum() > 0);

    BigDecimal totalCapital = new BigDecimal("100000.00");
    BigDecimal var95 = PortfolioRiskCalculator.calculateValueAtRisk95(portVol, totalCapital);
    BigDecimal es95 = PortfolioRiskCalculator.calculateExpectedShortfall95(portVol, totalCapital);

    assertNotNull(var95);
    assertNotNull(es95);
    assertTrue(var95.signum() > 0);
    assertTrue(es95.compareTo(var95) > 0); // CVaR is always strictly greater than VaR for the same confidence
  }
}
