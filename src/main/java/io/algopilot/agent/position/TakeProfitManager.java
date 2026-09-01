package io.algopilot.agent.position;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class TakeProfitManager {

  public boolean isTakeProfitTriggered(String side, BigDecimal currentPrice, BigDecimal takeProfit) {
    if (takeProfit == null || takeProfit.compareTo(BigDecimal.ZERO) <= 0 || currentPrice == null) {
      return false;
    }
    if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(takeProfit) >= 0;
    } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
      return currentPrice.compareTo(takeProfit) <= 0;
    }
    return false;
  }
}
