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
import io.algopilot.fill.FillStore;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.reconciliation.broker.BrokerStateProviderException;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import io.algopilot.reconciliation.engine.ReconciliationEngine;
import io.algopilot.reconciliation.model.BotReconciliationStatus;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationResult;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReconciliationServiceTest {
  private ReconciliationEngine engine;
  private ReconciliationStore store;
  private BrokerStateProvider brokerStateProvider;
  private BotStore botStore;
  private OrderStore orderStore;
  private FillStore fillStore;
  private PositionStore positionStore;
  private AuditEventWriter audit;
  private Clock clock;
  private ReconciliationService service;

  private UUID botId;
  private Bot bot;
  private Instant fixedInstant;

  @BeforeEach
  void setUp() {
    engine = new ReconciliationEngine();
    store = mock(ReconciliationStore.class);
    brokerStateProvider = mock(BrokerStateProvider.class);
    botStore = mock(BotStore.class);
    orderStore = mock(OrderStore.class);
    fillStore = mock(FillStore.class);
    positionStore = mock(PositionStore.class);
    audit = mock(AuditEventWriter.class);
    fixedInstant = Instant.parse("2026-08-31T12:00:00Z");
    clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    service = new ReconciliationService(
        engine, store, brokerStateProvider, botStore, orderStore, fillStore, positionStore, audit, clock
    );

    botId = UUID.randomUUID();
    bot = new Bot(botId, "BTC Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
  }

  @Test
  void testReconcile_matched_completesSuccessfullyAndAudits() {
    when(orderStore.findOpenOrdersByBotId(botId.toString())).thenReturn(List.of());
    when(fillStore.findByBotId(botId.toString())).thenReturn(List.of());
    when(positionStore.findByBotId(botId.toString())).thenReturn(List.of());

    BrokerStateSnapshot brokerSnapshot = new BrokerStateSnapshot(
        Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString(),
        new BrokerAccountBalance("USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, fixedInstant),
        List.of(), List.of(), List.of(), fixedInstant
    );
    when(brokerStateProvider.fetchSnapshot(eq(Broker.ALPACA_PAPER), eq(ExecutionMode.PAPER), eq(botId.toString())))
        .thenReturn(brokerSnapshot);

    ReconciliationResult result = service.reconcile(botId.toString());

    assertTrue(result.isMatched());
    assertEquals(ReconciliationStatus.MATCHED, result.run().status());
    assertEquals(0, result.run().mismatchCount());

    verify(store).saveRun(any(ReconciliationRun.class));
    verify(store).updateRun(argThat(r -> r.status() == ReconciliationStatus.MATCHED));
    verify(audit).record(eq("SYSTEM"), eq(botId.toString()), eq("RECONCILIATION_MATCHED"), eq("BOT"), eq(botId.toString()), anyMap());
    verify(botStore, never()).updateStatus(any(), eq(BotStatus.PAUSED));
  }

  @Test
  void testReconcile_criticalMismatch_pausesRunningBotAndAudits() {
    when(orderStore.findOpenOrdersByBotId(botId.toString())).thenReturn(List.of());
    when(fillStore.findByBotId(botId.toString())).thenReturn(List.of());
    when(positionStore.findByBotId(botId.toString())).thenReturn(List.of(
        new Position(UUID.randomUUID(), botId.toString(), "BTC/USD", new BigDecimal("2.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, fixedInstant)
    ));

    // Broker has 0 positions -> Critical position mismatch
    BrokerStateSnapshot brokerSnapshot = new BrokerStateSnapshot(
        Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString(),
        new BrokerAccountBalance("USD", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("120000.00"), fixedInstant),
        List.of(), List.of(), List.of(), fixedInstant
    );
    when(brokerStateProvider.fetchSnapshot(eq(Broker.ALPACA_PAPER), eq(ExecutionMode.PAPER), eq(botId.toString())))
        .thenReturn(brokerSnapshot);

    ReconciliationResult result = service.reconcile(botId.toString());

    assertFalse(result.isMatched());
    assertTrue(result.hasCriticalMismatches());
    assertEquals(ReconciliationStatus.MISMATCHED, result.run().status());
    assertEquals(1, result.mismatches().size());
    assertEquals(MismatchType.POSITION_QUANTITY_MISMATCH, result.mismatches().getFirst().mismatchType());

    // Verified Bot Pause Safety Response
    verify(botStore).updateStatus(botId, BotStatus.PAUSED);
    verify(store).saveMismatches(argThat(list -> list.size() == 1));
    verify(audit).record(eq("SYSTEM"), eq(botId.toString()), eq("BOT_PAUSED_RECONCILIATION"), eq("BOT"), eq(botId.toString()), anyMap());
    verify(audit).record(eq("SYSTEM"), eq(botId.toString()), eq("RECONCILIATION_MISMATCH_CRITICAL"), eq("BOT"), eq(botId.toString()), anyMap());
  }

  @Test
  void testReconcile_brokerProviderFailure_classifiedAsFailedAndPausesBot() {
    when(brokerStateProvider.fetchSnapshot(any(), any(), any()))
        .thenThrow(new BrokerStateProviderException("CONNECTION_TIMEOUT"));

    assertThrows(ReconciliationException.class, () -> service.reconcile(botId.toString()));

    verify(store).updateRun(argThat(r -> r.status() == ReconciliationStatus.FAILED && r.errorDetail().contains("CONNECTION_TIMEOUT")));
    verify(botStore).updateStatus(botId, BotStatus.PAUSED);
    verify(audit).record(eq("SYSTEM"), eq(botId.toString()), eq("RECONCILIATION_FAILED"), eq("BOT"), eq(botId.toString()), anyMap());
  }

  @Test
  void testReconcile_nonExistentBot_throwsBotNotFound() {
    UUID unknown = UUID.randomUUID();
    when(botStore.findById(unknown)).thenReturn(Optional.empty());

    assertThrows(BotNotFoundException.class, () -> service.reconcile(unknown.toString()));
  }

  @Test
  void testGetBotReconciliationStatus_returnsAccurateStatus() {
    ReconciliationRun latestRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MISMATCHED, 1, "Mismatches detected: 1", fixedInstant, fixedInstant, fixedInstant
    );
    when(store.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(latestRun));

    ReconciliationMismatch mismatch = new ReconciliationMismatch(
        UUID.randomUUID(), latestRun.id(), botId.toString(),
        MismatchCategory.POSITION_MISMATCH, MismatchType.POSITION_QUANTITY_MISMATCH,
        MismatchSeverity.CRITICAL, "BTC/USD", Collections.emptyMap(), Collections.emptyMap(),
        ResolutionState.UNRESOLVED, null, fixedInstant
    );
    when(store.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of(mismatch));

    BotReconciliationStatus status = service.getBotReconciliationStatus(botId.toString());

    assertEquals(botId.toString(), status.botId());
    assertEquals(ReconciliationStatus.MISMATCHED, status.latestStatus());
    assertEquals(1, status.unresolvedCriticalCount());
    assertTrue(status.recoveryRequired());
  }
}
