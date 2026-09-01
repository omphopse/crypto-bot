package io.algopilot.agent.state;

public class InvalidStateTransitionException extends RuntimeException {
  private final AgentState fromState;
  private final AgentState toState;

  public InvalidStateTransitionException(AgentState fromState, AgentState toState, String reason) {
    super("Illegal agent state transition from " + fromState + " to " + toState + ": " + reason);
    this.fromState = fromState;
    this.toState = toState;
  }

  public AgentState getFromState() {
    return fromState;
  }

  public AgentState getToState() {
    return toState;
  }
}
