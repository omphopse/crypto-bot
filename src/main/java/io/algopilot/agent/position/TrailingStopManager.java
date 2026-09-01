package io.algopilot.agent.position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class TrailingStopManager {

  public BigDecimal calculateTrailingStop(String side, BigDecimal currentPrice, BigDecimal highWaterMark, BigDecimal trailingStopPct) {
    if (currentPrice == null || trailingStopPct == null || trailingStopPct.compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }

    if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
      BigDecimal effectiveHwm = highWaterMark != null ? highWaterMark.max(currentPrice) : currentPrice;
      BigDecimal dropAmount = effectiveHwm.multiply(trailingStopPct).setScale(6, RoundingMode.HALF_UP);
      return effectiveHwm.subtract(dropAmount);
    } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
      BigDecimal effectiveLwm = highWaterMark != null ? highWaterMark.min(currentPrice) : currentPrice;
      BigDecimal riseAmount = effectiveLwm.multiply(trailingStopPct).setScale(6, RoundingMode.HALF_UP);
      return effectiveLwm.add(riseAmount);
    }
    return null;
  }

  public boolean isTrailingStopTriggered(String side, BigDecimal currentPrice, BigDecimal trailingStop) {
    if (trailingStop == null || currentPrice == null) {
      return false;
    }
    if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(trailingStop) <= 0;
    } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(trailingStop) >= 0;
    }
    return false;
  }
}
