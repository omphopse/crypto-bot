package io.algopilot.strategy;

import java.util.Optional;
import java.util.UUID;

public interface StrategyStore {
  Strategy saveStrategy(Strategy strategy);
  StrategyVersion saveVersion(StrategyVersion version);
  Optional<StrategyVersion> findVersionById(UUID id);
  int latestVersionNumber(UUID strategyId);
}
