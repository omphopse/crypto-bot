package io.algopilot.portfolio.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RiskParityAllocatorTest {

  @Test
  void testCalculateInverseVolatilityWeights_assignsLowerWeightToHigherVolatility() {
    // Asset A has vol 0.02 (2%), Asset B has vol 0.04 (4%)
    // InvVol(A) = 50, InvVol(B) = 25. Total = 75.
    // TargetWeight(A) = 50/75 = 66.67%, TargetWeight(B) = 25/75 = 33.33%
    Map<String, BigDecimal> vols = Map.of(
        "A", new BigDecimal("0.02"),
        "B", new BigDecimal("0.04")
    );

    Map<String, BigDecimal> weights = RiskParityAllocator.calculateInverseVolatilityWeights(vols);

    assertNotNull(weights);
    assertEquals(2, weights.size());
    assertTrue(weights.get("A").compareTo(weights.get("B")) > 0);

    BigDecimal sum = weights.get("A").add(weights.get("B"));
    assertEquals(new BigDecimal("1.0000"), sum);
  }
}
