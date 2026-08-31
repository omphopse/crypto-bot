package io.algopilot.portfolio.allocation.persistence;

import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioAllocationStore {
  void savePlan(PortfolioAllocationPlan plan);

  Optional<PortfolioAllocationPlan> findPlanById(UUID id);

  Optional<PortfolioAllocationPlan> findLatestPlan();

  List<PortfolioAllocationPlan> findRecentPlans(int limit);
}
