package io.algopilot.agent.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeterministicPositionSizerTest {

  private DeterministicPositionSizer sizer;

  @BeforeEach
  void setUp() {
    sizer = new DeterministicPositionSizer();
  }

  @Test
  void testAbsurdLlmQuantityIsOverwrittenWithDeterministicSize() {
    BigDecimal price = new BigDecimal("78200.00");
    BigDecimal accountEquity = new BigDecimal("100000.00");
    BigDecimal buyingPower = new BigDecimal("100000.00");
    BigDecimal maxPositionPercent = new BigDecimal("10.0");
    BigDecimal minOrderSize = new BigDecimal("0.00001");

    // Sizer computes deterministic quantity regardless of any LLM proposed 10,000 BTC
    BigDecimal calculatedQty = sizer.calculateBuyQuantity(
        "BTC/USD", price, accountEquity, buyingPower, maxPositionPercent, minOrderSize
    );

    assertThat(calculatedQty).isNotNull();
    assertThat(calculatedQty).isGreaterThan(BigDecimal.ZERO);

    BigDecimal notional = calculatedQty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    // Must satisfy Alpaca crypto minimum notional >= $10.00
    assertThat(notional).isGreaterThanOrEqualTo(new BigDecimal("10.00"));
    // Must be close to target notional $10.20
    assertThat(notional).isBetween(new BigDecimal("10.15"), new BigDecimal("10.25"));
    // Quantity must be vastly smaller than 10,000 BTC
    assertThat(calculatedQty).isLessThan(new BigDecimal("0.01"));
  }

  @Test
  void testMicroAccountCanarySizing() {
    BigDecimal price = new BigDecimal("78000.00");
    BigDecimal accountEquity = new BigDecimal("99.50");
    BigDecimal buyingPower = new BigDecimal("99.50");
    BigDecimal maxPositionPercent = new BigDecimal("12.0");
    BigDecimal minOrderSize = new BigDecimal("0.00001");

    BigDecimal calculatedQty = sizer.calculateBuyQuantity(
        "BTC/USD", price, accountEquity, buyingPower, maxPositionPercent, minOrderSize
    );

    BigDecimal notional = calculatedQty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    // Alpaca requirement
    assertThat(notional).isGreaterThanOrEqualTo(new BigDecimal("10.00"));
    // Canary micro risk limit requirement (12% of $99.50 is $11.94)
    assertThat(notional).isLessThanOrEqualTo(new BigDecimal("11.94"));
  }

  @Test
  void testExitSizingMatchesHeldPosition_ignoringLlmProposed() {
    BigDecimal heldPositionQty = new BigDecimal("0.00013077");
    BigDecimal absurdLlmProposed = new BigDecimal("10000.00");

    BigDecimal exitQty = sizer.calculateExitQuantity(
        "BTC/USD", TradeAction.SELL, heldPositionQty, absurdLlmProposed
    );

    assertThat(exitQty).isEqualByComparingTo(heldPositionQty);
  }

  @Test
  void testExitSizingWithNoPositionReturnsZero() {
    BigDecimal exitQtyNull = sizer.calculateExitQuantity(
        "BTC/USD", TradeAction.SELL, null, new BigDecimal("1.0")
    );
    assertThat(exitQtyNull).isEqualByComparingTo(BigDecimal.ZERO);

    BigDecimal exitQtyZero = sizer.calculateExitQuantity(
        "BTC/USD", TradeAction.SELL, BigDecimal.ZERO, new BigDecimal("1.0")
    );
    assertThat(exitQtyZero).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void testReduceSizingClampsToHeldPosition() {
    BigDecimal heldPositionQty = new BigDecimal("0.00013077");

    // Partial reduce
    BigDecimal partialProposed = new BigDecimal("0.00005000");
    BigDecimal reduceQty = sizer.calculateExitQuantity(
        "BTC/USD", TradeAction.REDUCE, heldPositionQty, partialProposed
    );
    assertThat(reduceQty).isEqualByComparingTo(partialProposed);

    // Over-reduce clamped to held
    BigDecimal excessiveProposed = new BigDecimal("1.00000000");
    BigDecimal clampedQty = sizer.calculateExitQuantity(
        "BTC/USD", TradeAction.REDUCE, heldPositionQty, excessiveProposed
    );
    assertThat(clampedQty).isEqualByComparingTo(heldPositionQty);
  }

  @Test
  void testNonPositivePriceThrowsException() {
    assertThatThrownBy(() -> sizer.calculateBuyQuantity(
        "BTC/USD", BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("0.00001")
    )).isInstanceOf(IllegalArgumentException.class);
  }
}
