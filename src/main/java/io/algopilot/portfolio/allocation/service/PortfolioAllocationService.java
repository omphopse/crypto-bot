package io.algopilot.portfolio.allocation.service;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.model.Candle;
import io.algopilot.portfolio.allocation.CorrelationMatrixCalculator;
import io.algopilot.portfolio.allocation.PortfolioRiskCalculator;
import io.algopilot.portfolio.allocation.RiskParityAllocator;
import io.algopilot.portfolio.allocation.model.AllocationWeight;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioAllocationService {
  private static final Logger log = LoggerFactory.getLogger(PortfolioAllocationService.class);

  private final PortfolioAllocationStore store;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public PortfolioAllocationService(PortfolioAllocationStore store, AuditEventWriter audit) {
    this(store, audit, Clock.systemUTC());
  }

  public PortfolioAllocationService(PortfolioAllocationStore store, AuditEventWriter audit, Clock clock) {
    this.store = store;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public PortfolioAllocationPlan generateAllocationPlan(
      List<String> symbols,
      BigDecimal totalCapital,
      Map<String, List<Candle>> historicalData
  ) {
    if (symbols == null || symbols.isEmpty()) {
      throw new IllegalArgumentException("SYMBOLS_LIST_REQUIRED");
    }
    BigDecimal capital = totalCapital != null ? totalCapital : new BigDecimal("100000.00");

    Map<String, List<BigDecimal>> returnsMap = new HashMap<>();
    Map<String, BigDecimal> volatilities = new HashMap<>();

    for (String symbol : symbols) {
      List<Candle> candles = historicalData != null ? historicalData.get(symbol) : null;
      List<BigDecimal> returns = candles != null ? CorrelationMatrixCalculator.calculateReturns(candles) : List.of();
      returnsMap.put(symbol, returns);
      BigDecimal vol = returns.size() >= 2 ? CorrelationMatrixCalculator.calculateStdDev(returns) : new BigDecimal("0.0200");
      volatilities.put(symbol, vol);
    }

    // Correlation & Risk Parity
    Map<String, Map<String, BigDecimal>> correlationMatrix = CorrelationMatrixCalculator.buildCorrelationMatrix(returnsMap);
    Map<String, BigDecimal> targetWeights = RiskParityAllocator.calculateInverseVolatilityWeights(volatilities);

    BigDecimal portfolioVol = PortfolioRiskCalculator.calculatePortfolioVolatility(targetWeights, volatilities, correlationMatrix);
    BigDecimal var95 = PortfolioRiskCalculator.calculateValueAtRisk95(portfolioVol, capital);
    BigDecimal es95 = PortfolioRiskCalculator.calculateExpectedShortfall95(portfolioVol, capital);

    BigDecimal currentWeightEqual = BigDecimal.ONE.divide(BigDecimal.valueOf(symbols.size()), 4, RoundingMode.HALF_UP);
    List<AllocationWeight> weightsList = new ArrayList<>();

    for (String symbol : symbols) {
      BigDecimal targetWeight = targetWeights.getOrDefault(symbol, currentWeightEqual);
      BigDecimal targetCap = capital.multiply(targetWeight).setScale(4, RoundingMode.HALF_UP);
      BigDecimal currentCap = capital.multiply(currentWeightEqual).setScale(4, RoundingMode.HALF_UP);
      BigDecimal deltaCap = targetCap.subtract(currentCap);

      weightsList.add(new AllocationWeight(
          symbol,
          targetWeight,
          currentWeightEqual,
          targetCap,
          currentCap,
          deltaCap
      ));
    }

    StringBuilder rationale = new StringBuilder("Risk-parity inverse-volatility allocation. ");
    for (AllocationWeight w : weightsList) {
      rationale.append(w.symbol()).append(": target ").append(w.targetWeight().multiply(BigDecimal.valueOf(100)).toPlainString()).append("%, ");
    }

    PortfolioAllocationPlan plan = new PortfolioAllocationPlan(
        UUID.randomUUID(),
        capital,
        portfolioVol,
        var95,
        es95,
        weightsList,
        rationale.toString().trim(),
        clock.instant()
    );

    store.savePlan(plan);

    audit.record(
        "PORTFOLIO_ALLOCATION",
        "SYSTEM",
        "PORTFOLIO_ALLOCATION_GENERATED",
        "ALLOCATION_PLAN",
        plan.id().toString(),
        Map.of(
            "totalCapital", capital.toPlainString(),
            "portfolioVolatility", portfolioVol.toPlainString(),
            "valueAtRisk95", var95.toPlainString(),
            "expectedShortfall95", es95.toPlainString(),
            "assetCount", symbols.size()
        )
    );

    log.info("PORTFOLIO_ALLOCATION_GENERATED planId={} capital={} vol={} var95={}", plan.id(), capital, portfolioVol, var95);
    return plan;
  }

  public Optional<PortfolioAllocationPlan> getLatestPlan() {
    return store.findLatestPlan();
  }

  public Optional<PortfolioAllocationPlan> getPlan(UUID id) {
    return store.findPlanById(id);
  }

  public List<PortfolioAllocationPlan> getRecentPlans(int limit) {
    return store.findRecentPlans(limit);
  }
}
