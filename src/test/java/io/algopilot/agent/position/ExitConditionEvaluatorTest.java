package io.algopilot.agent.position;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExitConditionEvaluatorTest {
  private ExitConditionEvaluator evaluator;
  private PositionLifecycleRecord openLongPosition;

  @BeforeEach
  void setUp() {
    StopLossManager stopLossManager = new StopLossManager();
    TakeProfitManager takeProfitManager = new TakeProfitManager();
    TrailingStopManager trailingStopManager = new TrailingStopManager();
    evaluator = new ExitConditionEvaluator(stopLossManager, takeProfitManager, trailingStopManager);

    Instant now = Instant.parse("2026-09-01T12:00:00Z");
    openLongPosition = new PositionLifecycleRecord(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BTC/USD", "BUY",
        new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("60000.00"),
        new BigDecimal("58000.00"), new BigDecimal("58000.00"), new BigDecimal("65000.00"),
        new BigDecimal("0.03"), new BigDecimal("60000.00"), PositionLifecycleState.MONITORING,
        now, null, now
    );
  }

  @Test
  void testEvaluate_priceNominal_noExit() {
    BigDecimal currentPrice = new BigDecimal("61000.00");
    PositionExitEvaluation eval = evaluator.evaluate(openLongPosition, currentPrice, null);

    assertThat(eval.shouldExit()).isFalse();
    assertThat(eval.action()).isEqualTo(TradeAction.HOLD);
    assertThat(eval.reason()).isEqualTo(ExitReason.NONE);
  }

  @Test
  void testEvaluate_hardStopLossTriggered() {
    BigDecimal currentPrice = new BigDecimal("57900.00"); // below 58000.00 stop
    PositionExitEvaluation eval = evaluator.evaluate(openLongPosition, currentPrice, null);

    assertThat(eval.shouldExit()).isTrue();
    assertThat(eval.action()).isEqualTo(TradeAction.CLOSE);
    assertThat(eval.reason()).isEqualTo(ExitReason.HARD_STOP_LOSS);
    assertThat(eval.exitQuantity()).isEqualByComparingTo("1.00");
  }

  @Test
  void testEvaluate_takeProfitTriggered() {
    BigDecimal currentPrice = new BigDecimal("65500.00"); // above 65000.00 take profit
    PositionExitEvaluation eval = evaluator.evaluate(openLongPosition, currentPrice, null);

    assertThat(eval.shouldExit()).isTrue();
    assertThat(eval.action()).isEqualTo(TradeAction.CLOSE);
    assertThat(eval.reason()).isEqualTo(ExitReason.TAKE_PROFIT);
    assertThat(eval.exitQuantity()).isEqualByComparingTo("1.00");
  }

  @Test
  void testEvaluate_trailingStopTriggered() {
    // Position had HWM of 70000.00, trailing stop 3% is 67900.00
    PositionLifecycleRecord posWithHwm = new PositionLifecycleRecord(
        openLongPosition.positionId(), openLongPosition.botId(), openLongPosition.strategyVersionId(),
        "BTC/USD", "BUY", new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("60000.00"),
        new BigDecimal("58000.00"), new BigDecimal("58000.00"), new BigDecimal("80000.00"),
        new BigDecimal("0.03"), new BigDecimal("70000.00"), PositionLifecycleState.MONITORING,
        Instant.now(), null, Instant.now()
    );

    BigDecimal currentPrice = new BigDecimal("67500.00"); // dropped below 67900 trailing stop
    PositionExitEvaluation eval = evaluator.evaluate(posWithHwm, currentPrice, null);

    assertThat(eval.shouldExit()).isTrue();
    assertThat(eval.action()).isEqualTo(TradeAction.CLOSE);
    assertThat(eval.reason()).isEqualTo(ExitReason.TRAILING_STOP);
  }
}
