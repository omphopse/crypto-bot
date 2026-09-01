package io.algopilot.agent;

import java.util.List;

public interface AgentDecisionStore {
  AgentDecision save(AgentDecision decision);
  default List<AgentDecision> findRecent(int limit) { return List.of(); }
  default java.util.Optional<AgentDecision> findById(java.util.UUID id) { return findRecent(100).stream().filter(d -> d.id().equals(id)).findFirst(); }
}
