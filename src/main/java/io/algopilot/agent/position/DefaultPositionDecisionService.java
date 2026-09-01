package io.algopilot.agent.position;

import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.LLMDecisionEngineService;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.decision.ValidationStatus;
import io.algopilot.agent.execution.PositionDecisionService;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultPositionDecisionService implements PositionDecisionService {
  private static final Logger log = LoggerFactory.getLogger(DefaultPositionDecisionService.class);

  private final LLMDecisionEngineService decisionEngine;

  public DefaultPositionDecisionService(LLMDecisionEngineService decisionEngine) {
    this.decisionEngine = decisionEngine;
  }

  @Override
  public Optional<TradeAction> evaluateExit(PositionContext position, TradingContext context) {
    if (position == null || position.quantity().compareTo(BigDecimal.ZERO) == 0 || context == null) {
      return Optional.of(TradeAction.HOLD);
    }

    try {
      StructuredTradeDecision decision = decisionEngine.analyze(context);
      if (decision.validationStatus() != ValidationStatus.VALIDATED) {
        log.warn("AI decision not validated during position exit evaluation: status={}", decision.validationStatus());
        return Optional.of(TradeAction.HOLD);
      }

      if (decision.decision() == TradeAction.CLOSE || decision.decision() == TradeAction.REDUCE) {
        return Optional.of(decision.decision());
      }
      return Optional.of(TradeAction.HOLD);
    } catch (Exception e) {
      log.error("Failed to evaluate AI position decision: {}", e.getMessage(), e);
      return Optional.of(TradeAction.HOLD);
    }
  }
}
