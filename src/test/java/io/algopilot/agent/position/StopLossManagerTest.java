package io.algopilot.agent.position;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StopLossManagerTest {
  private StopLossManager manager;

  @BeforeEach
  void setUp() {
    manager = new StopLossManager();
  }

  @Test
  void testIsValidStopMove_longPosition_raisingStop_valid() {
    BigDecimal currentPrice = new BigDecimal("65000.00");
    BigDecimal oldStop = new BigDecimal("58000.00");
    BigDecimal newStop = new BigDecimal("62000.00");

    boolean valid = manager.isValidStopMove("BUY", currentPrice, oldStop, newStop);
    assertThat(valid).isTrue();
  }

  @Test
  void testIsValidStopMove_longPosition_loweringStop_invalid() {
    BigDecimal currentPrice = new BigDecimal("65000.00");
    BigDecimal oldStop = new BigDecimal("58000.00");
    BigDecimal newStop = new BigDecimal("55000.00"); // Risky: lowering stop loss!

    boolean valid = manager.isValidStopMove("BUY", currentPrice, oldStop, newStop);
    assertThat(valid).isFalse();
  }

  @Test
  void testIsValidStopMove_longPosition_stopAboveCurrentPrice_invalid() {
    BigDecimal currentPrice = new BigDecimal("65000.00");
    BigDecimal oldStop = new BigDecimal("58000.00");
    BigDecimal newStop = new BigDecimal("66000.00"); // Impossible: stop above market price!

    boolean valid = manager.isValidStopMove("BUY", currentPrice, oldStop, newStop);
    assertThat(valid).isFalse();
  }

  @Test
  void testIsStopTriggered_longPosition() {
    BigDecimal stopLoss = new BigDecimal("58000.00");

    assertThat(manager.isStopTriggered("BUY", new BigDecimal("59000.00"), stopLoss)).isFalse();
    assertThat(manager.isStopTriggered("BUY", new BigDecimal("58000.00"), stopLoss)).isTrue();
    assertThat(manager.isStopTriggered("BUY", new BigDecimal("57500.00"), stopLoss)).isTrue();
  }
}
