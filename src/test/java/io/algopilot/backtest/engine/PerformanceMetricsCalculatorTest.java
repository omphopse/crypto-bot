package io.algopilot.backtest.engine;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.backtest.model.EquityPoint;
import io.algopilot.backtest.model.SimulatedTrade;
import io.algopilot.risk.RiskDecisionRequest.Side;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PerformanceMetricsCalculatorTest {

  @Test
  void testCalculateMaxDrawdownPct_accuratelyDetectsPeakToTroughLoss() {
    Instant now = Instant.now();
    List<EquityPoint> equityCurve = List.of(
        new EquityPoint(now, new BigDecimal("100000.00"), BigDecimal.ZERO),
        new EquityPoint(now.plusSeconds(1), new BigDecimal("110000.00"), BigDecimal.ZERO), // Peak
        new EquityPoint(now.plusSeconds(2), new BigDecimal("99000.00"), BigDecimal.ZERO),  // 110k -> 99k = 10% DD
        new EquityPoint(now.plusSeconds(3), new BigDecimal("105000.00"), BigDecimal.ZERO)
    );

    BigDecimal maxDd = PerformanceMetricsCalculator.calculateMaxDrawdownPct(equityCurve);
    assertEquals(new BigDecimal("10.0000"), maxDd);
  }

  @Test
  void testCalculateProfitFactor_grossProfitsDividedByGrossLosses() {
    Instant now = Instant.now();
    UUID backtestId = UUID.randomUUID();

    List<SimulatedTrade> trades = List.of(
        new SimulatedTrade(UUID.randomUUID(), backtestId, "BTC", Side.BUY, now, now, new BigDecimal("100"), new BigDecimal("150"), BigDecimal.ONE, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"), "EXIT"),
        new SimulatedTrade(UUID.randomUUID(), backtestId, "BTC", Side.BUY, now, now, new BigDecimal("100"), new BigDecimal("130"), BigDecimal.ONE, new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"), "EXIT"),
        new SimulatedTrade(UUID.randomUUID(), backtestId, "BTC", Side.BUY, now, now, new BigDecimal("100"), new BigDecimal("80"), BigDecimal.ONE, new BigDecimal("-20.00"), BigDecimal.ZERO, new BigDecimal("-20.00"), "EXIT")
    );

    // Gross profit = 50 + 30 = 80. Gross loss = 20. Profit factor = 80 / 20 = 4.0000
    BigDecimal pf = PerformanceMetricsCalculator.calculateProfitFactor(trades);
    assertEquals(new BigDecimal("4.0000"), pf);
  }

  @Test
  void testCalculateWinRate() {
    Instant now = Instant.now();
    UUID backtestId = UUID.randomUUID();

    List<SimulatedTrade> trades = List.of(
        new SimulatedTrade(UUID.randomUUID(), backtestId, "BTC", Side.BUY, now, now, new BigDecimal("100"), new BigDecimal("150"), BigDecimal.ONE, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"), "EXIT"),
        new SimulatedTrade(UUID.randomUUID(), backtestId, "BTC", Side.BUY, now, now, new BigDecimal("100"), new BigDecimal("80"), BigDecimal.ONE, new BigDecimal("-20.00"), BigDecimal.ZERO, new BigDecimal("-20.00"), "EXIT")
    );

    // 1 win out of 2 trades = 50.00%
    BigDecimal winRate = PerformanceMetricsCalculator.calculateWinRate(trades);
    assertEquals(new BigDecimal("50.0000"), winRate);
  }
}
