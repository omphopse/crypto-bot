package io.algopilot.agent.state;

/**
 * Explicit lifecycle states for Algopilot autonomous trading agents.
 */
public enum AgentState {
  IDLE,
  OBSERVING,
  SCANNING,
  RESEARCHING,
  ANALYZING,
  DECIDING,
  RISK_CHECK,
  EXECUTING,
  MONITORING,
  EXIT_EVALUATION,
  PAUSED,
  ERROR,
  STOPPED
}
