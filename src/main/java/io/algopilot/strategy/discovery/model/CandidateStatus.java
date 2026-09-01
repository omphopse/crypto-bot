package io.algopilot.strategy.discovery.model;

public enum CandidateStatus {
  GENERATED,
  BACKTESTING,
  VALIDATING,
  ROBUSTNESS_CHECK,
  PAPER_PENDING,
  PAPER_RUNNING,
  QUALIFIED,
  REJECTED,
  RETIRED
}
