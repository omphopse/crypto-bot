package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotDeploymentException;
import io.algopilot.bot.BotService;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.DeployBotRequest;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.fill.FillStore;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.reconciliation.engine.ReconciliationEngine;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationException;
import io.algopilot.reconciliation.service.ReconciliationService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LiveTradingBoundaryTest {
  private BotStore botStore;
  private AuditEventWriter audit;
  private BotService botService;
  private ReconciliationService reconciliationService;

  @BeforeEach
  void setUp() {
    botStore = mock(BotStore.class);
    audit = mock(AuditEventWriter.class);
    botService = new BotService(botStore, audit);

    ReconciliationEngine engine = new ReconciliationEngine();
    ReconciliationStore reconciliationStore = mock(ReconciliationStore.class);
    BrokerStateProvider brokerProvider = mock(BrokerStateProvider.class);
    OrderStore orderStore = mock(OrderStore.class);
    FillStore fillStore = mock(FillStore.class);
    PositionStore positionStore = mock(PositionStore.class);

    reconciliationService = new ReconciliationService(
        engine, reconciliationStore, brokerProvider, botStore, orderStore, fillStore, positionStore, audit
    );
  }

  @Test
  void testDeployBot_liveModeStrictlyRejected() {
    DeployBotRequest liveRequest = new DeployBotRequest("Live Alpha", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.LIVE);
    BotDeploymentException ex = assertThrows(BotDeploymentException.class, () -> botService.deploy(liveRequest));
    assertEquals("LIVE_TRADING_DISABLED", ex.getMessage());
  }

  @Test
  void testDeployBot_mismatchedBrokerAndMode_rejected() {
    DeployBotRequest badAlpaca = new DeployBotRequest("Alpaca Demo", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.DEMO);
    assertEquals("BROKER_MODE_MISMATCH", assertThrows(BotDeploymentException.class, () -> botService.deploy(badAlpaca)).getMessage());

    DeployBotRequest badBybit = new DeployBotRequest("Bybit Paper", UUID.randomUUID(), Broker.BYBIT_DEMO, ExecutionMode.PAPER);
    assertEquals("BROKER_MODE_MISMATCH", assertThrows(BotDeploymentException.class, () -> botService.deploy(badBybit)).getMessage());
  }

  @Test
  void testReconciliation_liveModeBot_rejected() {
    UUID botId = UUID.randomUUID();
    Bot liveBot = new Bot(botId, "Live Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.LIVE, BotStatus.RUNNING, Instant.now());
    when(botStore.findById(botId)).thenReturn(Optional.of(liveBot));

    ReconciliationException ex = assertThrows(ReconciliationException.class, () -> reconciliationService.reconcile(botId.toString()));
    assertEquals("LIVE_TRADING_DISABLED", ex.getMessage());
  }
}
