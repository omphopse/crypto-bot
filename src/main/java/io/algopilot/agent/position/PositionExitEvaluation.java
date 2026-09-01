package io.algopilot.agent.position;

import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;

public record PositionExitEvaluation(
    boolean shouldExit,
    TradeAction action,
    ExitReason reason,
    BigDecimal exitQuantity,
    BigDecimal triggerPrice,
    String explanation
) {
  public static PositionExitEvaluation noExit() {
    return new PositionExitEvaluation(false, TradeAction.HOLD, ExitReason.NONE, BigDecimal.ZERO, BigDecimal.ZERO, "Position conditions nominal.");
  }
}
