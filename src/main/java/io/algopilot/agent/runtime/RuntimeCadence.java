package io.algopilot.agent.runtime;

public enum RuntimeCadence {
  FAST(5000L),
  NORMAL(15000L),
  CONSERVATIVE(60000L);

  private final long intervalMs;

  RuntimeCadence(long intervalMs) {
    this.intervalMs = intervalMs;
  }

  public long intervalMs() {
    return intervalMs;
  }
}
