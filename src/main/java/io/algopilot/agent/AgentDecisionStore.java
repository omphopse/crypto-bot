package io.algopilot.agent;

import java.util.List;

public interface AgentDecisionStore {
  AgentDecision save(AgentDecision decision);
  default List<AgentDecision> findRecent(int limit) { return List.of(); }
}
