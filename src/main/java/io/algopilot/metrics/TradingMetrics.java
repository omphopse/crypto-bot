package io.algopilot.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TradingMetrics {
  private final Counter ordersSubmittedCounter;
  private final Counter ordersExecutedCounter;
  private final Counter riskApprovedCounter;
  private final Counter riskRejectedCounter;
  private final Counter rebalanceRunsCounter;
  private final AtomicInteger activeReconciliationMismatches;

  public TradingMetrics(MeterRegistry registry) {
    this.ordersSubmittedCounter = Counter.builder("algopilot.orders.submitted.count")
        .description("Total number of orders submitted to risk evaluation")
        .register(registry);

    this.ordersExecutedCounter = Counter.builder("algopilot.orders.executed.count")
        .description("Total number of orders successfully executed via execution gateway")
        .register(registry);

    this.riskApprovedCounter = Counter.builder("algopilot.risk.decisions.count")
        .tag("status", "APPROVED")
        .description("Total number of risk decisions approved")
        .register(registry);

    this.riskRejectedCounter = Counter.builder("algopilot.risk.decisions.count")
        .tag("status", "REJECTED")
        .description("Total number of risk decisions rejected")
        .register(registry);

    this.rebalanceRunsCounter = Counter.builder("algopilot.rebalance.runs.count")
        .description("Total number of portfolio rebalancing runs executed")
        .register(registry);

    this.activeReconciliationMismatches = new AtomicInteger(0);
    registry.gauge("algopilot.reconciliation.mismatches.active", activeReconciliationMismatches);
  }

  public void recordOrderSubmitted() {
    ordersSubmittedCounter.increment();
  }

  public void recordOrderExecuted() {
    ordersExecutedCounter.increment();
  }

  public void recordRiskDecision(boolean approved) {
    if (approved) {
      riskApprovedCounter.increment();
    } else {
      riskRejectedCounter.increment();
    }
  }

  public void recordRebalanceRun() {
    rebalanceRunsCounter.increment();
  }

  public void setActiveReconciliationMismatches(int count) {
    activeReconciliationMismatches.set(Math.max(0, count));
  }

  public int getActiveReconciliationMismatches() {
    return activeReconciliationMismatches.get();
  }
}
