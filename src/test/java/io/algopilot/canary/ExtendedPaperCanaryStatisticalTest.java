package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtendedPaperCanaryStatisticalTest {
  private CanaryAnalyticsService analyticsService;

  @BeforeEach
  void setUp() {
    analyticsService = new CanaryAnalyticsService();
  }

  @Test
  void testGenerateExtended1000TradeReport_verifiesCompleteStatisticalMetrics() {
    CanaryAnalyticsService.ExtendedCanaryReport report = analyticsService.generateExtended1000TradeReport();

    assertThat(report).isNotNull();
    assertThat(report.totalTrades()).isEqualTo(1000);
    assertThat(report.winCount()).isEqualTo(628);
    assertThat(report.lossCount()).isEqualTo(372);
    assertThat(report.winRatePct()).isEqualByComparingTo("62.80");
    assertThat(report.profitFactor()).isEqualByComparingTo("1.76");
    assertThat(report.netExpectancyBps()).isEqualByComparingTo("31.8");

    // 10 Sequential blocks
    assertThat(report.sequentialBlocks()).hasSize(10);
    assertThat(report.sequentialBlocks().get(0).startTrade()).isEqualTo(1);
    assertThat(report.sequentialBlocks().get(0).endTrade()).isEqualTo(100);
    assertThat(report.sequentialBlocks().get(9).startTrade()).isEqualTo(901);
    assertThat(report.sequentialBlocks().get(9).endTrade()).isEqualTo(1000);

    // Confidence intervals
    assertThat(report.winRateCi95().confidenceLevel()).isEqualTo(0.95);
    assertThat(report.winRateCi95().lowerBound()).isLessThan(report.winRatePct());
    assertThat(report.winRateCi95().upperBound()).isGreaterThan(report.winRatePct());

    // Cost sensitivity
    assertThat(report.costScenarios()).hasSize(4);
    assertThat(report.costScenarios().get(0).isProfitable()).isTrue();
    assertThat(report.costScenarios().get(3).netExpectancy()).isGreaterThan(BigDecimal.ZERO);

    // Drift and conclusion
    assertThat(report.driftClassification()).isEqualTo("HEALTHY");
    assertThat(report.conclusion()).isEqualTo("POSITIVE SAMPLE");
  }
}
