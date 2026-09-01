package io.algopilot.strategy.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.strategy.discovery.model.ScenarioEstimate;
import io.algopilot.strategy.discovery.service.EconomicScenarioCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EconomicScenarioCalculatorTest {
  private EconomicScenarioCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new EconomicScenarioCalculator();
  }

  @Test
  void testCalculateScenario_forTenDollarsPerDay() {
    ScenarioEstimate estimate = calculator.calculateScenario(
        new BigDecimal("10.00"),
        new BigDecimal("0.0020"), // 20 bps edge
        5 // 5 trades per day
    );

    assertThat(estimate).isNotNull();
    assertThat(estimate.requiredCapital()).isGreaterThan(BigDecimal.ZERO);
    assertThat(estimate.disclaimer()).contains("HISTORICAL SCENARIO ESTIMATE ONLY");
    assertThat(estimate.worstDayEstimate()).isLessThan(BigDecimal.ZERO);
    assertThat(estimate.bestDayEstimate()).isGreaterThan(BigDecimal.ZERO);
  }
}
