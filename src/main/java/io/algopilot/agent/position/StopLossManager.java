package io.algopilot.agent.position;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class StopLossManager {

  public boolean isValidStopMove(String side, BigDecimal currentPrice, BigDecimal oldStop, BigDecimal newStop) {
    if (newStop == null || newStop.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }

    if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
      // Long position: stop must be below current price
      if (newStop.compareTo(currentPrice) >= 0) {
        return false;
      }
      // Long position: stop can only move UP (reducing risk), never DOWN
      if (oldStop != null && newStop.compareTo(oldStop) < 0) {
        return false;
      }
      return true;
    } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
      // Short position: stop must be above current price
      if (newStop.compareTo(currentPrice) <= 0) {
        return false;
      }
      // Short position: stop can only move DOWN (reducing risk), never UP
      if (oldStop != null && newStop.compareTo(oldStop) > 0) {
        return false;
      }
      return true;
    }
    return false;
  }

  public boolean isStopTriggered(String side, BigDecimal currentPrice, BigDecimal stopLoss) {
    if (stopLoss == null || stopLoss.compareTo(BigDecimal.ZERO) <= 0 || currentPrice == null) {
      return false;
    }
    if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(stopLoss) <= 0;
    } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(stopLoss) >= 0;
    }
    return false;
  }
}
