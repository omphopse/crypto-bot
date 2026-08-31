package io.algopilot.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Deterministic and deliberately side-effect free. Execution must require an APPROVED result. */
@Service
public class RiskEngine {
  private final Clock clock;
  private final RiskLimits limits;
  public RiskEngine() { this(Clock.systemUTC(), RiskLimits.defaults()); }
  RiskEngine(Clock clock, RiskLimits limits) { this.clock = clock; this.limits = limits; }

  public RiskDecision evaluate(RiskDecisionRequest request) {
    List<RiskDecision.Reason> reasons = new ArrayList<>();
    Instant now = clock.instant();
    BigDecimal proposed = request.quantity().multiply(request.referencePrice());
    BigDecimal positionPct = percent(request.existingSymbolExposure().add(proposed), request.accountEquity());
    BigDecimal portfolioPct = percent(request.existingPortfolioExposure().add(proposed), request.accountEquity());
    BigDecimal dailyLossPct = percent(request.realizedDailyLoss(), request.accountEquity());
    if (request.emergencyStop()) reasons.add(RiskDecision.Reason.EMERGENCY_STOP);
    if (request.botPaused()) reasons.add(RiskDecision.Reason.BOT_PAUSED);
    if (request.duplicateOrder()) reasons.add(RiskDecision.Reason.DUPLICATE_ORDER);
    if (request.marketDataTimestamp().plus(limits.maxMarketDataAge()).isBefore(now)) reasons.add(RiskDecision.Reason.STALE_MARKET_DATA);
    if (positionPct.compareTo(limits.maxPositionPercent()) > 0) reasons.add(RiskDecision.Reason.MAX_POSITION_SIZE);
    if (portfolioPct.compareTo(limits.maxPortfolioExposurePercent()) > 0) reasons.add(RiskDecision.Reason.MAX_PORTFOLIO_EXPOSURE);
    if (dailyLossPct.compareTo(limits.maxDailyLossPercent()) > 0) reasons.add(RiskDecision.Reason.MAX_DAILY_LOSS_EXCEEDED);
    if (request.drawdownPercent().compareTo(limits.maxDrawdownPercent()) > 0) reasons.add(RiskDecision.Reason.MAX_DRAWDOWN);
    if (request.estimatedSpreadPercent().compareTo(limits.maxSpreadPercent()) > 0) reasons.add(RiskDecision.Reason.SPREAD_TOO_LARGE);
    if (request.estimatedSlippagePercent().compareTo(limits.maxSlippagePercent()) > 0) reasons.add(RiskDecision.Reason.SLIPPAGE_TOO_LARGE);
    if (request.openTrades() >= limits.maxOpenTrades()) reasons.add(RiskDecision.Reason.MAX_OPEN_TRADES);
    if (request.tradesToday() >= limits.maxTradesPerDay()) reasons.add(RiskDecision.Reason.MAX_TRADES_PER_DAY);
    if (request.consecutiveLosses() >= limits.maxConsecutiveLosses()) reasons.add(RiskDecision.Reason.MAX_CONSECUTIVE_LOSSES);
    return new RiskDecision(reasons.isEmpty() ? RiskDecision.Status.APPROVED : RiskDecision.Status.REJECTED, List.copyOf(reasons), request.clientOrderId(), now);
  }
  private BigDecimal percent(BigDecimal value, BigDecimal total) {
    if (total.signum() == 0) return new BigDecimal("999999");
    return value.multiply(new BigDecimal("100")).divide(total, 8, RoundingMode.HALF_UP);
  }
}
