package io.algopilot.cost;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CostStore {
  AiCostEvent saveCostEvent(AiCostEvent event);
  List<AiCostEvent> findCostEventsByBotId(UUID botId, int limit);
  List<AiCostEvent> findRecentCostEvents(int limit);

  void savePricing(AiModelPricing pricing);
  Optional<AiModelPricing> findActivePricing(String provider, String model, Instant when);

  void saveBudgetPolicy(AiBudgetPolicy policy);
  List<AiBudgetPolicy> findBudgetPolicies();
  Optional<AiBudgetPolicy> findBudgetPolicy(BudgetTier tier, String targetId);

  CostSummary getCostSummaryToday(Instant now);
  CostSummary getCostSummaryByBotId(UUID botId, Instant now);
  CostSummary getCostSummaryByStrategyId(UUID strategyId, Instant now);
}
