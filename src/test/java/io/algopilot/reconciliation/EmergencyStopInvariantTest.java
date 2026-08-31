package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotControlException;
import io.algopilot.bot.BotControlService;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderRejectedException;
import io.algopilot.order.OrderService;
import io.algopilot.order.OrderStore;
import io.algopilot.reconciliation.model.RecoveryRequest;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationRecoveryService;
import io.algopilot.risk.RiskDecision;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskDecisionService;
import io.algopilot.risk.RiskEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EmergencyStopInvariantTest {
  private BotStore botStore;
  private BotControlService botControlService;
  private ReconciliationStore reconciliationStore;
  private ReconciliationRecoveryService recoveryService;
  private AuditEventWriter audit;
  private OrderService orderService;
  private RiskDecisionService riskDecisionService;
  private OrderStore orderStore;

  private UUID botId;
  private Instant now;

  @BeforeEach
  void setUp() {
    botStore = mock(BotStore.class);
    audit = mock(AuditEventWriter.class);
    botControlService = new BotControlService(botStore, audit);
    reconciliationStore = mock(ReconciliationStore.class);
    recoveryService = new ReconciliationRecoveryService(reconciliationStore, botStore, audit);
    now = Instant.now();
    botId = UUID.randomUUID();

    riskDecisionService = mock(RiskDecisionService.class);
    orderStore = mock(OrderStore.class);
    orderService = new OrderService(riskDecisionService, orderStore, audit);
  }

  @Test
  void testEmergencyStoppedBot_cannotResumeThroughOrdinaryControlsEvenIfReconciliationMatched() {
    Bot emergencyBot = new Bot(botId, "Alpha Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.EMERGENCY_STOPPED, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(emergencyBot));

    // 1. Ordinary resume throws exception
    BotControlException controlEx = assertThrows(
        BotControlException.class,
        () -> botControlService.resume(botId)
    );
    assertEquals("EMERGENCY_STOP_REQUIRES_RECONCILIATION", controlEx.getMessage());

    // 2. Even when a subsequent reconciliation run returns MATCHED:
    ReconciliationRun matchedRun = new ReconciliationRun(
        UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER,
        ReconciliationStatus.MATCHED, 0, null, now, now, now
    );
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(matchedRun));

    // 3. Reconciliation recovery MUST STILL REJECT emergency stopped bot:
    ReconciliationException recoveryEx = assertThrows(
        ReconciliationException.class,
        () -> recoveryService.recover(new RecoveryRequest(botId.toString(), "operator", "Try recovery"))
    );
    assertEquals("EMERGENCY_STOP_CANNOT_RESUME_VIA_RECONCILIATION", recoveryEx.getMessage());

    // 4. And ordinary resume MUST STILL REJECT
    assertThrows(BotControlException.class, () -> botControlService.resume(botId));
  }

  @Test
  void testBotPausedByReconciliation_preventsNewOrders() {
    // When bot is paused (e.g. following a reconciliation mismatch)
    RiskEngine riskEngine = new RiskEngine();
    RiskDecisionRequest request = new RiskDecisionRequest(
        "order-1", botId.toString(), "strat-v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY,
        new BigDecimal("1.0"), new BigDecimal("60000.00"),
        new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        now,
        true, // botPaused = true
        false, false, 0, 0, 0
    );

    RiskDecision decision = riskEngine.evaluate(request);
    assertEquals(RiskDecision.Status.REJECTED, decision.status());
    assertTrue(decision.reasons().contains(RiskDecision.Reason.BOT_PAUSED));

    when(riskDecisionService.evaluate(request)).thenReturn(decision);
    assertThrows(OrderRejectedException.class, () -> orderService.create(request));
    verify(orderStore, never()).save(any(OrderRecord.class));
  }
}
