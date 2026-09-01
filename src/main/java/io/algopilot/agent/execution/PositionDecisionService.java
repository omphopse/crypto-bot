package io.algopilot.agent.execution;

import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.TradeAction;
import java.util.Optional;

public interface PositionDecisionService {
  Optional<TradeAction> evaluateExit(PositionContext position, TradingContext context);
}
