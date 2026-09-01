package io.algopilot.agent.decision;

import io.algopilot.agent.context.TradingContext;

public interface LLMDecisionProvider {
  String providerName();
  String modelName();
  StructuredTradeDecision analyze(TradingContext context);
}
