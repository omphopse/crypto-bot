package io.algopilot.agent.state;

/**
 * Autonomous operational modes.
 * Live trading is permanently locked out (LIVE_LOCKED).
 */
public enum AutonomousMode {
  OFF,
  OBSERVE_ONLY,
  PAPER_AUTONOMOUS,
  DEMO_AUTONOMOUS,
  LIVE_LOCKED
}
