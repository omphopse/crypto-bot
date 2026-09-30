package io.algopilot.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RiskEngine {
  private final Clock clock;
  private final RiskLimits limits;

  public RiskEngine() {
    this(Clock.systemUTC(), RiskLimits.defaults());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public RiskEngine(@org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this(clock != null ? clock : Clock.systemUTC(), RiskLimits.defaults());
  }
  public RiskEngine(Clock clock, RiskLimits limits) { this.clock = clock != null ? clock : Clock.systemUTC(); this.limits = limits; }

  public RiskDecision evaluate(RiskDecisionRequest request) {
    List<RiskDecision.Reason> reasons = new ArrayList<>();
    Instant now = clock.instant();
    BigDecimal proposed = request.quantity().multiply(request.referencePrice());
    BigDecimal nextSymbolExposure = request.side() == RiskDecisionRequest.Side.SELL
        ? request.existingSymbolExposure().subtract(proposed).max(BigDecimal.ZERO)
        : request.existingSymbolExposure().add(proposed);
    BigDecimal nextPortfolioExposure = request.side() == RiskDecisionRequest.Side.SELL
        ? request.existingPortfolioExposure().subtract(proposed).max(BigDecimal.ZERO)
        : request.existingPortfolioExposure().add(proposed);
    BigDecimal positionPct = percent(nextSymbolExposure, request.accountEquity());
    BigDecimal portfolioPct = percent(nextPortfolioExposure, request.accountEquity());
    BigDecimal dailyLossPct = percent(request.realizedDailyLoss(), request.accountEquity());
    if (request.emergencyStop()) reasons.add(RiskDecision.Reason.EMERGENCY_STOP);
    if (request.botPaused()) {
      boolean isExposureReducingSell = request.side() == RiskDecisionRequest.Side.SELL
          && request.existingSymbolExposure() != null
          && request.existingSymbolExposure().compareTo(BigDecimal.ZERO) > 0
          && proposed.compareTo(request.existingSymbolExposure().multiply(new BigDecimal("1.01"))) <= 0;
      if (!isExposureReducingSell) {
        reasons.add(RiskDecision.Reason.BOT_PAUSED);
      }
    }
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
