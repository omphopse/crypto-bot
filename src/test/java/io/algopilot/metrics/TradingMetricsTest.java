package io.algopilot.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TradingMetricsTest {
  private SimpleMeterRegistry registry;
  private TradingMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new TradingMetrics(registry);
  }

  @Test
  void testMetricsRecording_incrementsCountersAndGauges() {
    metrics.recordOrderSubmitted();
    metrics.recordOrderExecuted();
    metrics.recordRiskDecision(true);
    metrics.recordRiskDecision(false);
    metrics.recordRebalanceRun();
    metrics.setActiveReconciliationMismatches(3);

    assertEquals(1.0, registry.get("algopilot.orders.submitted.count").counter().count());
    assertEquals(1.0, registry.get("algopilot.orders.executed.count").counter().count());
    assertEquals(1.0, registry.get("algopilot.risk.decisions.count").tag("status", "APPROVED").counter().count());
    assertEquals(1.0, registry.get("algopilot.risk.decisions.count").tag("status", "REJECTED").counter().count());
    assertEquals(1.0, registry.get("algopilot.rebalance.runs.count").counter().count());
    assertEquals(3.0, registry.get("algopilot.reconciliation.mismatches.active").gauge().value());
  }
}
