package io.algopilot.portfolio.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public final class RiskParityAllocator {

  private RiskParityAllocator() {}

  public static Map<String, BigDecimal> calculateInverseVolatilityWeights(Map<String, BigDecimal> assetVolatilities) {
    Map<String, BigDecimal> weights = new HashMap<>();
    if (assetVolatilities == null || assetVolatilities.isEmpty()) {
      return weights;
    }

    int n = assetVolatilities.size();
    boolean allZero = assetVolatilities.values().stream().allMatch(v -> v == null || v.signum() <= 0);
    if (allZero) {
      BigDecimal equalWeight = BigDecimal.ONE.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
      for (String symbol : assetVolatilities.keySet()) {
        weights.put(symbol, equalWeight);
      }
      return weights;
    }

    Map<String, BigDecimal> invVolMap = new HashMap<>();
    BigDecimal totalInvVol = BigDecimal.ZERO;

    for (Map.Entry<String, BigDecimal> entry : assetVolatilities.entrySet()) {
      BigDecimal vol = entry.getValue();
      if (vol != null && vol.signum() > 0) {
        BigDecimal invVol = BigDecimal.ONE.divide(vol, 8, RoundingMode.HALF_UP);
        invVolMap.put(entry.getKey(), invVol);
        totalInvVol = totalInvVol.add(invVol);
      } else {
        // Fallback for zero volatility asset: use average inverse volatility
        invVolMap.put(entry.getKey(), BigDecimal.ONE);
        totalInvVol = totalInvVol.add(BigDecimal.ONE);
      }
    }

    BigDecimal weightSum = BigDecimal.ZERO;
    String lastSymbol = null;

    for (Map.Entry<String, BigDecimal> entry : invVolMap.entrySet()) {
      BigDecimal weight = entry.getValue().divide(totalInvVol, 4, RoundingMode.HALF_UP);
      weights.put(entry.getKey(), weight);
      weightSum = weightSum.add(weight);
      lastSymbol = entry.getKey();
    }

    // Ensure sum-to-1.0000 invariant
    BigDecimal diff = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP).subtract(weightSum);
    if (diff.signum() != 0 && lastSymbol != null) {
      weights.put(lastSymbol, weights.get(lastSymbol).add(diff).setScale(4, RoundingMode.HALF_UP));
    }

    return weights;
  }
}
