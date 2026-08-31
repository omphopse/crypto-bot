package io.algopilot.risk;

import java.time.Instant;
import java.util.List;

public record RiskDecision(Status status, List<Reason> reasons, String clientOrderId, Instant evaluatedAt) {
  public enum Status { APPROVED, REJECTED }
  public enum Reason { EMERGENCY_STOP, BOT_PAUSED, DUPLICATE_ORDER, STALE_MARKET_DATA, MAX_POSITION_SIZE,
    MAX_PORTFOLIO_EXPOSURE, MAX_DAILY_LOSS_EXCEEDED, MAX_DRAWDOWN, SPREAD_TOO_LARGE,
    SLIPPAGE_TOO_LARGE, MAX_OPEN_TRADES, MAX_TRADES_PER_DAY, MAX_CONSECUTIVE_LOSSES }
}
