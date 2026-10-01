package io.algopilot.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.order.OrderTransitionRequest;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExecutionGatewayExitSafetyTest {

  private BrokerOrderAdapter alpacaAdapter;
  private BotStore botStore;
  private OrderStore orderStore;
  private OrderLifecycleService lifecycleService;
  private AuditEventWriter audit;
  private ReconciliationStore reconciliationStore;
  private BrokerStateProvider brokerStateProvider;
  private ExecutionGateway gateway;
  private Instant fixedInstant;

  private final UUID botId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    alpacaAdapter = mock(BrokerOrderAdapter.class);
    when(alpacaAdapter.broker()).thenReturn(Broker.ALPACA_PAPER);
    when(alpacaAdapter.supportedMode()).thenReturn(ExecutionMode.PAPER);

    botStore = mock(BotStore.class);
    orderStore = mock(OrderStore.class);
    lifecycleService = mock(OrderLifecycleService.class);
    audit = mock(AuditEventWriter.class);
    reconciliationStore = mock(ReconciliationStore.class);
    brokerStateProvider = mock(BrokerStateProvider.class);

    fixedInstant = Instant.parse("2026-10-01T12:00:00Z");
    Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    gateway = new ExecutionGateway(
        List.of(alpacaAdapter),
        botStore,
        orderStore,
        lifecycleService,
        audit,
        reconciliationStore,
        brokerStateProvider,
        clock
    );
  }

  private ReconciliationMismatch createCriticalMismatch() {
    return new ReconciliationMismatch(
        UUID.randomUUID(),
        UUID.randomUUID(),
        botId.toString(),
        MismatchCategory.POSITION_MISMATCH,
        MismatchType.POSITION_QUANTITY_MISMATCH,
        MismatchSeverity.CRITICAL,
        "BTC/USD",
        Map.of(),
        Map.of(),
        ResolutionState.UNRESOLVED,
        null,
        fixedInstant,
        "PA36EX6RQWQT"
    );
  }

  @Test
  void testBuyBlockedWhenCriticalMismatch() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord buyOrder = new OrderRecord(
        orderId, "client-buy-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(buyOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(reconciliationStore.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of(createCriticalMismatch()));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("BOT_RECONCILIATION_MISMATCH_BLOCK"));
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testSellAllowedWhenCriticalMismatchAndPositionExists() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord sellOrder = new OrderRecord(
        orderId, "client-sell-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(sellOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(reconciliationStore.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of(createCriticalMismatch()));

    BrokerPosition heldPosition = new BrokerPosition(
        botId.toString(), "BTC/USD", new BigDecimal("0.001"),
        new BigDecimal("84000.00"), BigDecimal.ZERO, new BigDecimal("84.00"), fixedInstant
    );
    when(brokerStateProvider.fetchPositions(Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString()))
        .thenReturn(List.of(heldPosition));

    when(alpacaAdapter.submitOrder(sellOrder)).thenReturn(new OrderSubmissionResult(
        "client-sell-1", "alpaca-ex-sell-1", OrderStatus.ACKNOWLEDGED, fixedInstant, Map.of()
    ));

    OrderSubmissionResult result = gateway.dispatch(orderId);

    assertNotNull(result);
    assertEquals("alpaca-ex-sell-1", result.exchangeOrderId());
    assertEquals(OrderStatus.ACKNOWLEDGED, result.status());
    verify(alpacaAdapter).submitOrder(sellOrder);
    verify(lifecycleService).transition(eq(orderId), any(OrderTransitionRequest.class));
  }

  @Test
  void testSellBlockedWhenCriticalMismatchAndNoPosition() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord sellOrder = new OrderRecord(
        orderId, "client-sell-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(sellOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(reconciliationStore.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of(createCriticalMismatch()));

    when(brokerStateProvider.fetchPositions(Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString()))
        .thenReturn(List.of());

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("BOT_RECONCILIATION_MISMATCH_BLOCK"));
    assertTrue(ex.getMessage().contains("no broker position to sell"));
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testSellBlockedWhenQuantityExceedsBrokerPosition() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord sellOrder = new OrderRecord(
        orderId, "client-sell-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("0.005"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(sellOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(reconciliationStore.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of(createCriticalMismatch()));

    BrokerPosition heldPosition = new BrokerPosition(
        botId.toString(), "BTC/USD", new BigDecimal("0.002"),
        new BigDecimal("84000.00"), BigDecimal.ZERO, new BigDecimal("168.00"), fixedInstant
    );
    when(brokerStateProvider.fetchPositions(Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString()))
        .thenReturn(List.of(heldPosition));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("BOT_RECONCILIATION_MISMATCH_BLOCK"));
    assertTrue(ex.getMessage().contains("exceeds broker held quantity"));
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testSellAllowedWhenBotPausedAndPositionExists() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    OrderRecord sellOrder = new OrderRecord(
        orderId, "client-sell-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(sellOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(reconciliationStore.findMismatchesByBotId(botId.toString(), ResolutionState.UNRESOLVED))
        .thenReturn(List.of());

    BrokerPosition heldPosition = new BrokerPosition(
        botId.toString(), "BTC/USD", new BigDecimal("0.001"),
        new BigDecimal("84000.00"), BigDecimal.ZERO, new BigDecimal("84.00"), fixedInstant
    );
    when(brokerStateProvider.fetchPositions(Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString()))
        .thenReturn(List.of(heldPosition));

    when(alpacaAdapter.submitOrder(sellOrder)).thenReturn(new OrderSubmissionResult(
        "client-sell-1", "alpaca-ex-sell-paused", OrderStatus.ACKNOWLEDGED, fixedInstant, Map.of()
    ));

    OrderSubmissionResult result = gateway.dispatch(orderId);

    assertNotNull(result);
    assertEquals("alpaca-ex-sell-paused", result.exchangeOrderId());
    verify(alpacaAdapter).submitOrder(sellOrder);
  }

  @Test
  void testBuyBlockedWhenBotPaused() {
    Bot bot = new Bot(botId, "Paper Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    OrderRecord buyOrder = new OrderRecord(
        orderId, "client-buy-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(buyOrder));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER"));
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testLiveTradingBlockedRegardlessOfSideOrMismatch() {
    Bot liveBot = new Bot(botId, "Live Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.LIVE, BotStatus.RUNNING, fixedInstant);
    
    OrderRecord buyOrder = new OrderRecord(
        orderId, "client-buy-live", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );
    OrderRecord sellOrder = new OrderRecord(
        orderId, "client-sell-live", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.SELL, new BigDecimal("0.001"), new BigDecimal("84000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(botStore.findById(botId)).thenReturn(Optional.of(liveBot));
    when(orderStore.findById(orderId)).thenReturn(Optional.of(buyOrder));

    BrokerAdapterException exBuy = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertEquals("LIVE_TRADING_DISABLED", exBuy.getMessage());

    when(orderStore.findById(orderId)).thenReturn(Optional.of(sellOrder));
    BrokerAdapterException exSell = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertEquals("LIVE_TRADING_DISABLED", exSell.getMessage());

    verify(alpacaAdapter, never()).submitOrder(any());
  }
}
