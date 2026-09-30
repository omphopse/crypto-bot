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
  @Test void buy_increases_exposure_and_enforces_limit() {
    // Equity $1,000, maxPosition 10% ($100). Existing exposure $50.
    // BUY $60 proposed -> total $110 (11% > 10%) -> REJECTED with MAX_POSITION_SIZE
    RiskDecision rejected = engine.evaluate(new RiskDecisionRequest("buy-exceed", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("60"), new BigDecimal("1000"),
        new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(rejected.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(rejected.reasons()).contains(RiskDecision.Reason.MAX_POSITION_SIZE);

    // BUY $40 proposed -> total $90 (9% <= 10%) -> APPROVED
    RiskDecision approved = engine.evaluate(new RiskDecisionRequest("buy-safe", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("40"), new BigDecimal("1000"),
        new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(approved.status()).isEqualTo(RiskDecision.Status.APPROVED);
  }

  @Test void sell_partially_reduces_exposure() {
    // Equity $1,000, maxPosition 10% ($100). Existing exposure $80.
    // In previous buggy code, SELL $30 would do 80 + 30 = 110 (11%) -> REJECTED.
    // With correct accounting, SELL $30 reduces exposure to 80 - 30 = 50 (5%) -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-partial", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1"), new BigDecimal("30"), new BigDecimal("1000"),
        new BigDecimal("80"), new BigDecimal("80"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  @Test void sell_completely_closes_exposure() {
    // Equity $1,000, maxPosition 10% ($100). Existing exposure $100.
    // SELL $100 reduces exposure to 100 - 100 = 0 (0%) -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-full", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("1000"),
        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  @Test void sell_larger_than_existing_exposure_clamps_at_zero() {
    // Equity $1,000. Existing exposure $50.
    // SELL $80 -> 50 - 80 = -30, clamped to 0 -> 0% -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-oversize", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1"), new BigDecimal("80"), new BigDecimal("1000"),
        new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  @Test void existing_buy_risk_limit_behavior_remains_unchanged() {
    RiskDecisionRequest safe = request();
    assertThat(engine.evaluate(safe).status()).isEqualTo(RiskDecision.Status.APPROVED);

    // Exceeding portfolio exposure on BUY
    RiskDecision result = engine.evaluate(new RiskDecisionRequest(
        safe.clientOrderId(), safe.botId(), safe.strategyVersionId(), safe.symbol(),
        RiskDecisionRequest.Side.BUY, new BigDecimal("60"), new BigDecimal("10"), new BigDecimal("1000"),
        BigDecimal.ZERO, new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
        safe.estimatedSpreadPercent(), safe.estimatedSlippagePercent(), now, false, false, false, 0, 0, 0));
    assertThat(result.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(result.reasons()).contains(RiskDecision.Reason.MAX_PORTFOLIO_EXPOSURE);
  }

  @Test void evaluate_buy_whenBotPaused_rejectedWithBotPaused() {
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("buy-paused", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, true, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(decision.reasons()).contains(RiskDecision.Reason.BOT_PAUSED);
  }

  @Test void evaluate_sell_existingLong_whenBotPaused_approved() {
    // Existing exposure $1,000. SELL $500 while paused -> reduces existing long -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-paused-allowed", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("10000"),
        new BigDecimal("1000"), new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, true, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  @Test void evaluate_sell_zeroPosition_whenBotPaused_rejectedWithBotPaused() {
    // Existing exposure $0. SELL $100 while paused -> no position to reduce -> REJECTED with BOT_PAUSED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-paused-zero-pos", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, true, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(decision.reasons()).contains(RiskDecision.Reason.BOT_PAUSED);
  }

  @Test void evaluate_sell_largerThanHeldPosition_whenBotPaused_rejectedWithBotPaused() {
    // Existing exposure $100. SELL $150 while paused -> exceeds held exposure -> REJECTED with BOT_PAUSED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-paused-oversize", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1.5"), new BigDecimal("100"), new BigDecimal("10000"),
        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, true, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.REJECTED);
    assertThat(decision.reasons()).contains(RiskDecision.Reason.BOT_PAUSED);
  }

  @Test void evaluate_sell_fullCloseExistingLong_whenBotPaused_approved() {
    // Existing exposure $1,000. Full CLOSE SELL $1,000 while paused -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-paused-full-close", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("10000"),
        new BigDecimal("1000"), new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, true, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  @Test void evaluate_sell_activeBot_unaffectedByPausedRule() {
    // Active bot (botPaused=false). SELL $100 -> APPROVED.
    RiskDecision decision = engine.evaluate(new RiskDecisionRequest("sell-active", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"),
        new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, now, false, false, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.APPROVED);
    assertThat(decision.reasons()).isEmpty();
  }

  private RiskDecisionRequest request() { return new RiskDecisionRequest("safe-1", "bot-1", "momentum-v4", "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.02"), new BigDecimal("0.01"), now, false, false, false, 0, 0, 0); }
}
