package io.algopilot.agent.execution;

import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deterministic Position Sizer.
 * Strictly separates LLM directional reasoning (thesis, direction, confidence)
 * from execution-level risk and order sizing.
 * An uncalibrated or absurd quantity proposed by an LLM (e.g. 10,000 BTC) is completely discarded.
 */
@Component
public class DeterministicPositionSizer {
  private static final Logger log = LoggerFactory.getLogger(DeterministicPositionSizer.class);

  public static final BigDecimal DEFAULT_CRYPTO_TARGET_NOTIONAL = new BigDecimal("10.20");
  public static final BigDecimal ALPACA_CRYPTO_MIN_NOTIONAL = new BigDecimal("10.00");
  public static final BigDecimal DEFAULT_BTC_MIN_ORDER_SIZE = new BigDecimal("0.00001");
  public static final BigDecimal DEFAULT_MAX_POSITION_PERCENT = new BigDecimal("10.0");

  public BigDecimal calculateBuyQuantity(
      String symbol,
      BigDecimal freshPrice,
      BigDecimal accountEquity,
      BigDecimal buyingPower,
      BigDecimal maxPositionPercent,
      BigDecimal minOrderSize
  ) {
    if (freshPrice == null || freshPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Cannot size position with non-positive price: " + freshPrice);
    }

    BigDecimal equity = (accountEquity != null && accountEquity.compareTo(BigDecimal.ZERO) > 0)
        ? accountEquity : new BigDecimal("100.00");
    BigDecimal bp = (buyingPower != null && buyingPower.compareTo(BigDecimal.ZERO) > 0)
        ? buyingPower : equity;
    BigDecimal maxPct = (maxPositionPercent != null && maxPositionPercent.compareTo(BigDecimal.ZERO) > 0)
        ? maxPositionPercent : DEFAULT_MAX_POSITION_PERCENT;
    BigDecimal minSize = (minOrderSize != null && minOrderSize.compareTo(BigDecimal.ZERO) > 0)
        ? minOrderSize : DEFAULT_BTC_MIN_ORDER_SIZE;

    // Available capital is bounded by equity and buying power
    BigDecimal availableCapital = equity.min(bp);

    // Maximum risk-bounded notional
    BigDecimal maxRiskNotional = availableCapital.multiply(maxPct).divide(new BigDecimal("100"), 4, RoundingMode.DOWN);

    // Target notional calculation
    BigDecimal targetNotional;
    if (availableCapital.compareTo(new BigDecimal("150.00")) <= 0) {
      // Micro / Canary paper account: target slightly above broker minimum notional ($10.20)
      targetNotional = DEFAULT_CRYPTO_TARGET_NOTIONAL;
      if (targetNotional.compareTo(availableCapital) > 0) {
        targetNotional = availableCapital;
      }
    } else {
      // Standard paper/live account: target safe canary notional or risk limit
      targetNotional = DEFAULT_CRYPTO_TARGET_NOTIONAL.min(maxRiskNotional);
    }

    // Dynamic quantity calculation at live execution price
    BigDecimal qty = targetNotional.divide(freshPrice, 8, RoundingMode.UP);
    if (qty.compareTo(minSize) < 0) {
      qty = minSize;
    }

    BigDecimal finalQty = qty.setScale(8, RoundingMode.HALF_UP);
    log.info("DETERMINISTIC_BUY_SIZING symbol={} price={} targetNotional={} finalQty={}",
        symbol, freshPrice, targetNotional, finalQty);
    return finalQty;
  }

  public BigDecimal calculateExitQuantity(
      String symbol,
      TradeAction action,
      BigDecimal heldPositionQty,
      BigDecimal proposedQty
  ) {
    if (heldPositionQty == null || heldPositionQty.compareTo(BigDecimal.ZERO) <= 0) {
      log.warn("DETERMINISTIC_EXIT_SIZING_NO_POSITION symbol={} action={}", symbol, action);
      return BigDecimal.ZERO;
    }

    BigDecimal held = heldPositionQty.abs();
    if (action == TradeAction.REDUCE && proposedQty != null && proposedQty.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal reduceQty = proposedQty.min(held).setScale(8, RoundingMode.HALF_UP);
      log.info("DETERMINISTIC_REDUCE_SIZING symbol={} held={} reduceQty={}", symbol, held, reduceQty);
      return reduceQty;
    }

    // Full position exit for SELL or CLOSE
    BigDecimal exitQty = held.setScale(8, RoundingMode.HALF_UP);
    log.info("DETERMINISTIC_FULL_EXIT_SIZING symbol={} action={} exitQty={}", symbol, action, exitQty);
    return exitQty;
  }
}
