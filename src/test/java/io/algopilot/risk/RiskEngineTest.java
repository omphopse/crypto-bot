package io.algopilot.risk;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
  private final Instant now = Instant.parse("2026-08-31T12:00:00Z");
  private final RiskEngine engine = new RiskEngine(Clock.fixed(now, ZoneOffset.UTC), RiskLimits.defaults());

  @Test void approves_a_safe_typed_order() {
    assertThat(engine.evaluate(request()).status()).isEqualTo(RiskDecision.Status.APPROVED);
  }
  @Test void rejects_duplicate_and_stale_order_with_explicit_reasons() {
    RiskDecision result = engine.evaluate(new RiskDecisionRequest("a", "bot", "v1", "BTC/USD", RiskDecisionRequest.Side.BUY,
        new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now.minusSeconds(16), false, false, true, 0, 0, 0));
    assertThat(result.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(result.reasons()).containsExactlyInAnyOrder(RiskDecision.Reason.DUPLICATE_ORDER, RiskDecision.Reason.STALE_MARKET_DATA);
  }
  @Test void emergency_stop_cannot_be_bypassed() {
    RiskDecisionRequest safe = request();
    RiskDecision result = engine.evaluate(new RiskDecisionRequest(safe.clientOrderId(), safe.botId(), safe.strategyVersionId(), safe.symbol(), safe.side(), safe.quantity(), safe.referencePrice(), safe.accountEquity(), safe.existingSymbolExposure(), safe.existingPortfolioExposure(), safe.realizedDailyLoss(), safe.drawdownPercent(), safe.estimatedSpreadPercent(), safe.estimatedSlippagePercent(), safe.marketDataTimestamp(), false, true, false, 0, 0, 0));
    assertThat(result.reasons()).contains(RiskDecision.Reason.EMERGENCY_STOP);
  }
  private RiskDecisionRequest request() { return new RiskDecisionRequest("safe-1", "bot-1", "momentum-v4", "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.02"), new BigDecimal("0.01"), now, false, false, false, 0, 0, 0); }
}
