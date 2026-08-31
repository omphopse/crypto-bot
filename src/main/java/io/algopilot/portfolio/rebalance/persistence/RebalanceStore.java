package io.algopilot.portfolio.rebalance.persistence;

import io.algopilot.portfolio.rebalance.model.RebalanceOrder;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RebalanceStore {
  void saveRun(RebalanceRun run);

  void updateRun(RebalanceRun run);

  Optional<RebalanceRun> findRunById(UUID id);

  List<RebalanceRun> findRecentRuns(int limit);

  void saveOrder(RebalanceOrder order);

  List<RebalanceOrder> findOrdersByRunId(UUID runId);
}
