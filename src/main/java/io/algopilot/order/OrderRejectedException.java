package io.algopilot.order;

import io.algopilot.risk.RiskDecision;
public class OrderRejectedException extends RuntimeException {
  private final RiskDecision decision;
  public OrderRejectedException(RiskDecision decision) { super("Risk engine rejected order: " + (decision != null ? decision.reasons() : "")); this.decision = decision; }
  public RiskDecision decision() { return decision; }
}
