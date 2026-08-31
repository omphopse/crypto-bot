package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotNotFoundException;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.model.RecoveryRequest;
import io.algopilot.reconciliation.model.RecoveryResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationRecoveryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReconciliationRecoveryServiceTest {
  private ReconciliationStore store;
  private BotStore botStore;
  private AuditEventWriter audit;
  private Clock clock;
  private ReconciliationRecoveryService service;

  private UUID botId;
  private Instant fixedInstant;

  @BeforeEach
  void setUp() {
    store = mock(ReconciliationStore.class);
    botStore = mock(BotStore.class);
    audit = mock(AuditEventWriter.class);
    fixedInstant = Instant.parse("2026-08-31T14:00:00Z");
    clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    service = new ReconciliationRecoveryService(store, botStore, audit, clock);
    botId = UUID.randomUUID();
  }

  @Test
  void testRecovery_pausedBotWithMatchedState_succeedsAndAudits() {
    Bot pausedBot = new Bot(botId, "ETH Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(pausedBot));

    ReconciliationRun matchedRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, fixedInstant, fixedInstant, fixedInstant
    );
    when(store.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(matchedRun));

    RecoveryResult result = service.recover(new RecoveryRequest(botId.toString(), "maya", "State validated manual recovery"));

    assertNotNull(result);
    assertEquals("COMPLETED", result.status());
    assertEquals(botId.toString(), result.botId());

    verify(store).resolveAllUnresolvedMismatchesForBot(botId.toString(), fixedInstant);
    verify(store).saveRecovery(any(UUID.class), eq(botId.toString()), eq(matchedRun.id()), eq("maya"), eq("COMPLETED"), anyString(), eq(fixedInstant));
    verify(audit).record(eq("USER"), eq("maya"), eq("RECOVERY_COMPLETED"), eq("BOT"), eq(botId.toString()), anyMap());
  }

  @Test
  void testRecovery_emergencyStoppedBot_strictlyRejected() {
    Bot emergencyBot = new Bot(botId, "ETH Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.EMERGENCY_STOPPED, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(emergencyBot));

    // Even if latest reconciliation run is MATCHED:
    ReconciliationRun matchedRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, fixedInstant, fixedInstant, fixedInstant
    );
    when(store.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(matchedRun));

    ReconciliationException ex = assertThrows(
        ReconciliationException.class,
        () -> service.recover(new RecoveryRequest(botId.toString(), "operator", "Try recovery"))
    );

    assertTrue(ex.getMessage().contains("EMERGENCY_STOP_CANNOT_RESUME_VIA_RECONCILIATION"));
    verify(store, never()).resolveAllUnresolvedMismatchesForBot(any(), any());
    verify(audit, never()).record(any(), any(), eq("RECOVERY_COMPLETED"), any(), any(), any());
  }

  @Test
  void testRecovery_runningBot_rejected() {
    Bot runningBot = new Bot(botId, "ETH Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(runningBot));

    ReconciliationException ex = assertThrows(
        ReconciliationException.class,
        () -> service.recover(new RecoveryRequest(botId.toString(), "operator", "Try recovery"))
    );

    assertTrue(ex.getMessage().contains("BOT_NOT_PAUSED"));
  }

  @Test
  void testRecovery_latestRunMismatched_resolvesAndSucceeds() {
    Bot pausedBot = new Bot(botId, "ETH Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(pausedBot));

    ReconciliationRun mismatchedRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MISMATCHED, 2, "2 mismatches", fixedInstant, fixedInstant, fixedInstant
    );
    when(store.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(mismatchedRun));

    RecoveryResult result = service.recover(new RecoveryRequest(botId.toString(), "operator", "Resolve mismatches"));

    assertNotNull(result);
    assertEquals("COMPLETED", result.status());
    verify(store).resolveAllUnresolvedMismatchesForBot(botId.toString(), fixedInstant);
    verify(store).saveRun(argThat(run -> run.status() == ReconciliationStatus.MATCHED && run.mismatchCount() == 0));
  }

  @Test
  void testRecovery_noReconciliationRun_rejected() {
    Bot pausedBot = new Bot(botId, "ETH Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(pausedBot));
    when(store.findLatestRunByBotId(botId.toString())).thenReturn(Optional.empty());

    ReconciliationException ex = assertThrows(
        ReconciliationException.class,
        () -> service.recover(new RecoveryRequest(botId.toString(), "operator", "Try recovery"))
    );

    assertTrue(ex.getMessage().contains("NO_RECONCILIATION_RUN_FOUND"));
  }
}
