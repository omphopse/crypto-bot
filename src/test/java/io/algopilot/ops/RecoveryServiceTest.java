package io.algopilot.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.ops.recovery.RecoveryRun;
import io.algopilot.ops.recovery.RecoveryService;
import io.algopilot.ops.recovery.RecoveryStatus;
import io.algopilot.ops.recovery.RecoveryStore;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecoveryServiceTest {
  private MemoryRecoveryStore recoveryStore;
  private BotStore botStore;
  private ReconciliationService reconciliationService;
  private AuditEventWriter audit;
  private Clock clock;
  private RecoveryService recoveryService;
  private UUID botId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    recoveryStore = new MemoryRecoveryStore();
    botStore = mock(BotStore.class);
    reconciliationService = mock(ReconciliationService.class);
    audit = mock(AuditEventWriter.class);

    recoveryService = new RecoveryService(recoveryStore, botStore, reconciliationService, audit, clock);
    botId = UUID.randomUUID();
  }

  @Test
  void testRunRecovery_whenReconciliationClean_succeeds() {
    ReconciliationRun reconRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, now, now, now
    );
    when(reconciliationService.reconcile(botId.toString()))
        .thenReturn(new ReconciliationResult(reconRun, List.of()));

    RecoveryRun result = recoveryService.runRecovery(botId, "instance-1", "BROKER_RECONNECT");

    assertThat(result.status()).isEqualTo(RecoveryStatus.COMPLETED);
    verify(botStore, times(1)).updateStatus(botId, BotStatus.PAUSED);
  }

  @Test
  void testRunRecovery_whenReconciliationHasMismatches_failsAndRemainsPaused() {
    ReconciliationRun reconRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MISMATCHED, 1, "Discrepancy found", now, now, now
    );
    ReconciliationMismatch mismatch = new ReconciliationMismatch(
        UUID.randomUUID(), reconRun.id(), botId.toString(),
        io.algopilot.reconciliation.model.MismatchCategory.POSITION_MISMATCH,
        io.algopilot.reconciliation.model.MismatchType.POSITION_QUANTITY_MISMATCH,
        MismatchSeverity.CRITICAL, "BTC/USD",
        Map.of("quantity", 1.0), Map.of("quantity", 0.0),
        io.algopilot.reconciliation.model.ResolutionState.UNRESOLVED, null, now
    );
    when(reconciliationService.reconcile(botId.toString()))
        .thenReturn(new ReconciliationResult(reconRun, List.of(mismatch)));

    RecoveryRun result = recoveryService.runRecovery(botId, "instance-1", "BROKER_RECONNECT");

    assertThat(result.status()).isEqualTo(RecoveryStatus.FAILED);
    assertThat(result.stepDetails()).contains("1 reconciliation mismatches remain");
  }

  @Test
  void testHandleApplicationStartupRecovery_pausesPreviouslyRunningBots() {
    Bot runningBot = new Bot(botId, "Running Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findAll()).thenReturn(List.of(runningBot));

    recoveryService.handleApplicationStartupRecovery();

    verify(botStore, times(1)).updateStatus(botId, BotStatus.PAUSED);
  }

  private static final class MemoryRecoveryStore implements RecoveryStore {
    private final Map<UUID, RecoveryRun> map = Collections.synchronizedMap(new HashMap<>());

    @Override public RecoveryRun saveRun(RecoveryRun run) { map.put(run.id(), run); return run; }
    @Override public Optional<RecoveryRun> findLatestRunByBotId(UUID bId) { return map.values().stream().filter(r -> r.botId().equals(bId)).findFirst(); }
    @Override public List<RecoveryRun> findRecentRuns(int limit) { return new ArrayList<>(map.values()); }
  }
}
