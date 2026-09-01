package io.algopilot.agent.position;

public enum PositionLifecycleState {
  OPENING,
  OPEN,
  MONITORING,
  REDUCE_PENDING,
  CLOSING,
  CLOSED,
  ERROR,
  RECOVERY_REQUIRED
}
