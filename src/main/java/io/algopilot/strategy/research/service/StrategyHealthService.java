package io.algopilot.strategy.research.service;

import io.algopilot.strategy.research.model.StrategyHealthMetric;
import io.algopilot.strategy.research.persistence.ExperimentStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StrategyHealthService {
  private final ExperimentStore experimentStore;
  private final Clock clock;

  public StrategyHealthService(ExperimentStore experimentStore, Clock clock) {
    this.experimentStore = experimentStore;
    this.clock = clock;
  }

  public StrategyHealthMetric evaluateHealth(
      UUID strategyId,
      BigDecimal backtestExpectancy,
      BigDecimal paperExpectancy,
      BigDecimal demoExpectancy
  ) {
    Instant now = clock.instant();

    BigDecimal driftRatio = backtestExpectancy.signum() != 0
        ? paperExpectancy.subtract(backtestExpectancy).divide(backtestExpectancy.abs(), 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    String status = "HEALTHY";
    if (driftRatio.compareTo(new BigDecimal("-0.40")) < 0) {
      status = "STRATEGY_DEGRADATION";
    } else if (driftRatio.compareTo(new BigDecimal("-0.15")) < 0) {
      status = "DRIFT_DETECTED";
    }

    StrategyHealthMetric metric = new StrategyHealthMetric(
        UUID.randomUUID(), strategyId, backtestExpectancy, paperExpectancy, demoExpectancy, driftRatio, status, now
    );
    return experimentStore.saveStrategyHealth(metric);
  }

  public Optional<StrategyHealthMetric> getLatestHealth(UUID strategyId) {
    return experimentStore.findLatestStrategyHealth(strategyId);
  }
}
