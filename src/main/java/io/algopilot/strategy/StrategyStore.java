package io.algopilot.strategy;

import java.util.Optional;
import java.util.UUID;

public interface StrategyStore {
  Strategy saveStrategy(Strategy strategy);
  StrategyVersion saveVersion(StrategyVersion version);
  Optional<StrategyVersion> findVersionById(UUID id);
  default java.util.List<Strategy> findAllStrategies() { return java.util.List.of(); }
  default java.util.List<StrategyVersion> findAllVersions() { return java.util.List.of(); }
  int latestVersionNumber(UUID strategyId);
}
