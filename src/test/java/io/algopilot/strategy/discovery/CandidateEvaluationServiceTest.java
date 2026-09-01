package io.algopilot.strategy.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.algopilot.strategy.discovery.model.*;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import io.algopilot.strategy.discovery.service.CandidateEvaluationService;
import io.algopilot.strategy.discovery.service.CandidateStressService;
import io.algopilot.strategy.research.service.RealisticCostBacktestEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateEvaluationServiceTest {
  private CandidateStore candidateStore;
  private RealisticCostBacktestEngine backtestEngine;
  private CandidateStressService stressService;
  private CandidateEvaluationService evaluationService;

  private UUID candidateId;
  private Instant now;

  @BeforeEach
  void setUp() {
    candidateStore = mock(CandidateStore.class);
    backtestEngine = new RealisticCostBacktestEngine();
    stressService = new CandidateStressService(candidateStore, backtestEngine);
    evaluationService = new CandidateEvaluationService(candidateStore, backtestEngine, stressService);

    candidateId = UUID.randomUUID();
    now = Instant.parse("2026-09-02T12:00:00Z");

    StrategyCandidate candidate = new StrategyCandidate(
        candidateId, "fp-123", UUID.randomUUID(), "Test-Candidate", StrategyFamily.MOMENTUM,
        "BTC/USD", "1h", Map.of("fastEma", "9", "slowEma", "21"), GenerationMethod.PARAMETER_SWEEP,
        CandidateStatus.GENERATED, null, null, null, null, null, now
    );
    when(candidateStore.findCandidateById(candidateId)).thenReturn(Optional.of(candidate));
    when(candidateStore.saveCandidate(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void testEvaluateCandidate_performsBacktestAndAssignsRobustness() {
    StrategyCandidate evaluated = evaluationService.evaluateCandidate(candidateId, null);

    assertThat(evaluated).isNotNull();
    assertThat(evaluated.robustnessClassification()).isNotNull();
    assertThat(evaluated.robustnessScore()).isNotNull();
    assertThat(evaluated.status()).isIn(CandidateStatus.PAPER_PENDING, CandidateStatus.REJECTED);
  }
}
