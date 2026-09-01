package io.algopilot.agent.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutonomousExecutionStore {
  ValidatedTradeIntent saveIntent(ValidatedTradeIntent intent);
  StrategyValidationResult saveValidation(StrategyValidationResult result);
  AutonomousExecutionResult saveExecution(AutonomousExecutionResult execution);
  List<AutonomousExecutionResult> findRecentExecutionsByBotId(UUID botId, int limit);
  Optional<AutonomousExecutionResult> findLatestExecutionByBotId(UUID botId);
}
