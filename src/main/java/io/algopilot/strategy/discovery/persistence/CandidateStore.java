package io.algopilot.strategy.discovery.persistence;

import io.algopilot.strategy.discovery.model.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateStore {
  StrategyCandidate saveCandidate(StrategyCandidate candidate);
  Optional<StrategyCandidate> findCandidateById(UUID candidateId);
  Optional<StrategyCandidate> findCandidateByFingerprint(String fingerprint);
  List<StrategyCandidate> findAllCandidates();
  List<StrategyCandidate> findCandidatesByFamily(StrategyFamily family);
  List<StrategyCandidate> findRankedCandidates(int limit);

  void saveStressResults(List<StressResult> results);
  List<StressResult> findStressResultsByCandidateId(UUID candidateId);

  PaperValidationRecord savePaperValidation(PaperValidationRecord validation);
  Optional<PaperValidationRecord> findPaperValidationByCandidateId(UUID candidateId);
}
