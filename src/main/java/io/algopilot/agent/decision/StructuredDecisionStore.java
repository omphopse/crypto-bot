package io.algopilot.agent.decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StructuredDecisionStore {
  StructuredTradeDecision save(StructuredTradeDecision decision);
  Optional<StructuredTradeDecision> findLatestByBotId(UUID botId);
  List<StructuredTradeDecision> findRecentByBotId(UUID botId, int limit);
  List<StructuredTradeDecision> findAllRecent(int limit);
  Optional<StructuredTradeDecision> findById(UUID id);
}
