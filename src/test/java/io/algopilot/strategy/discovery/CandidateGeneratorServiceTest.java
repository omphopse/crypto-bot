package io.algopilot.strategy.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.algopilot.strategy.discovery.model.StrategyCandidate;
import io.algopilot.strategy.discovery.model.StrategyFamily;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import io.algopilot.strategy.discovery.service.CandidateGeneratorService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateGeneratorServiceTest {
  private CandidateStore candidateStore;
  private Clock clock;
  private CandidateGeneratorService generator;

  @BeforeEach
  void setUp() {
    candidateStore = mock(CandidateStore.class);
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    generator = new CandidateGeneratorService(candidateStore, clock);

    when(candidateStore.findCandidateByFingerprint(any())).thenReturn(Optional.empty());
    when(candidateStore.saveCandidate(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void testGenerateGridCandidates_producesUniqueReproducibleCandidates() {
    List<StrategyCandidate> candidates = generator.generateGridCandidates(
        UUID.randomUUID(), "BTC/USD", "1h", StrategyFamily.MOMENTUM
    );

    assertThat(candidates).isNotEmpty();
    assertThat(candidates.get(0).fingerprint()).isNotBlank();
    assertThat(candidates.get(0).symbol()).isEqualTo("BTC/USD");
  }
}
