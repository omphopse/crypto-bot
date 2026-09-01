package io.algopilot.market.scanner;

/**
 * Deterministic candidate condition classifications produced by MarketScanner.
 * These are opportunities/conditions only and NEVER represent executable orders.
 */
public enum CandidateType {
  MOMENTUM,
  TREND_CHANGE,
  BREAKOUT,
  MEAN_REVERSION,
  VOLUME_ANOMALY,
  VOLATILITY_EXPANSION,
  OVERSOLD,
  OVERBOUGHT,
  NO_CANDIDATE
}
