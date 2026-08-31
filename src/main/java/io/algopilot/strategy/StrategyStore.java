package io.algopilot.strategy;

import java.util.UUID;

public interface StrategyStore {
  Strategy saveStrategy(Strategy strategy);
  StrategyVersion saveVersion(StrategyVersion version);
  int latestVersionNumber(UUID strategyId);
}
