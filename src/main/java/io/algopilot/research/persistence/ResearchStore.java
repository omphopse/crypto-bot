package io.algopilot.research.persistence;

import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.model.StrategyCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchStore {
  void saveHypothesis(AlphaHypothesis hypothesis);

  Optional<AlphaHypothesis> findHypothesisById(UUID id);

  List<AlphaHypothesis> findRecentHypotheses(int limit);

  void saveCandidate(StrategyCandidate candidate);

  Optional<StrategyCandidate> findCandidateById(UUID id);

  List<StrategyCandidate> findRecentCandidates(int limit);
}
