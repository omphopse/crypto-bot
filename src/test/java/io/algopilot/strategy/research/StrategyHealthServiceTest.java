package io.algopilot.strategy.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.algopilot.strategy.research.model.StrategyHealthMetric;
import io.algopilot.strategy.research.persistence.ExperimentStore;
import io.algopilot.strategy.research.service.StrategyHealthService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrategyHealthServiceTest {
  private ExperimentStore experimentStore;
  private Clock clock;
  private StrategyHealthService healthService;
  private UUID strategyId;

  @BeforeEach
  void setUp() {
    experimentStore = mock(ExperimentStore.class);
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    healthService = new StrategyHealthService(experimentStore, clock);
    strategyId = UUID.randomUUID();

    when(experimentStore.saveStrategyHealth(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void testEvaluateHealth_whenPerformanceMatches_returnsHealthy() {
    StrategyHealthMetric metric = healthService.evaluateHealth(
        strategyId,
        new BigDecimal("0.0050"), // backtest expectancy
        new BigDecimal("0.0048"), // paper expectancy
        new BigDecimal("0.0049")  // demo expectancy
    );

    assertThat(metric.healthStatus()).isEqualTo("HEALTHY");
  }

  @Test
  void testEvaluateHealth_whenDegraded_returnsStrategyDegradation() {
    StrategyHealthMetric metric = healthService.evaluateHealth(
        strategyId,
        new BigDecimal("0.0100"), // backtest expectancy
        new BigDecimal("0.0020"), // paper expectancy (-80% degradation)
        new BigDecimal("0.0030")
    );

    assertThat(metric.healthStatus()).isEqualTo("STRATEGY_DEGRADATION");
  }
}
