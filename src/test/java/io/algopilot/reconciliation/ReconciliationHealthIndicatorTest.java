package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.health.ReconciliationHealthIndicator;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

public class ReconciliationHealthIndicatorTest {
  private ReconciliationStore store;
  private ReconciliationHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    store = mock(ReconciliationStore.class);
    healthIndicator = new ReconciliationHealthIndicator(store);
  }

  @Test
  void testHealth_noCriticalMismatches_reportsUp() {
    when(store.countUnresolvedMismatches(MismatchSeverity.CRITICAL)).thenReturn(0);
    when(store.countUnresolvedMismatches(null)).thenReturn(0);

    ReconciliationRun matchedRun = new ReconciliationRun(
        UUID.randomUUID(), "bot-1", Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, Instant.now(), Instant.now(), Instant.now()
    );
    when(store.findRecentRuns(1)).thenReturn(List.of(matchedRun));

    Health health = healthIndicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("HEALTHY", health.getDetails().get("reconciliationStatus"));
    assertEquals(0, health.getDetails().get("unresolvedCriticalMismatches"));
  }

  @Test
  void testHealth_activeCriticalMismatch_reportsDown() {
    when(store.countUnresolvedMismatches(MismatchSeverity.CRITICAL)).thenReturn(2);
    when(store.countUnresolvedMismatches(null)).thenReturn(2);

    ReconciliationRun mismatchedRun = new ReconciliationRun(
        UUID.randomUUID(), "bot-1", Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MISMATCHED, 2, "2 mismatches", Instant.now(), Instant.now(), Instant.now()
    );
    when(store.findRecentRuns(1)).thenReturn(List.of(mismatchedRun));

    Health health = healthIndicator.health();

    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("CRITICAL_MISMATCH_ACTIVE", health.getDetails().get("reconciliationStatus"));
    assertEquals(2, health.getDetails().get("unresolvedCriticalMismatches"));
  }
}
