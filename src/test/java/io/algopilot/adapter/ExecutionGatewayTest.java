package io.algopilot.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

public class ExecutionGatewayTest {
  private BrokerOrderAdapter alpacaAdapter;
  private BrokerOrderAdapter bybitAdapter;
  private BotStore botStore;
  private OrderStore orderStore;
  private OrderLifecycleService lifecycleService;
  private AuditEventWriter audit;
  private ExecutionGateway gateway;
  private Instant fixedInstant;

  @BeforeEach
  void setUp() {
    alpacaAdapter = mock(BrokerOrderAdapter.class);
    when(alpacaAdapter.broker()).thenReturn(Broker.ALPACA_PAPER);
    when(alpacaAdapter.supportedMode()).thenReturn(ExecutionMode.PAPER);

    bybitAdapter = mock(BrokerOrderAdapter.class);
    when(bybitAdapter.broker()).thenReturn(Broker.BYBIT_DEMO);
    when(bybitAdapter.supportedMode()).thenReturn(ExecutionMode.DEMO);

    botStore = mock(BotStore.class);
    orderStore = mock(OrderStore.class);
    lifecycleService = mock(OrderLifecycleService.class);
    audit = mock(AuditEventWriter.class);
    fixedInstant = Instant.parse("2026-08-31T12:00:00Z");
    Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    gateway = new ExecutionGateway(
        List.of(alpacaAdapter, bybitAdapter),
        botStore, orderStore, lifecycleService, audit, clock
    );
  }

  @Test
  void testDispatch_successfulAlpacaDispatch() {
    UUID botId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Bot bot = new Bot(botId, "Alpaca Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
    when(alpacaAdapter.submitOrder(order)).thenReturn(new OrderSubmissionResult(
        "client-ord-1", "alpaca-ex-1", OrderStatus.ACKNOWLEDGED, fixedInstant, Map.of()
    ));

    OrderSubmissionResult result = gateway.dispatch(orderId);

    assertNotNull(result);
    assertEquals("alpaca-ex-1", result.exchangeOrderId());
    assertEquals(OrderStatus.ACKNOWLEDGED, result.status());

    verify(lifecycleService).transition(eq(orderId), any(OrderTransitionRequest.class));
    verify(audit).record(eq("EXECUTION"), eq(botId.toString()), eq("ORDER_DISPATCHED_TO_BROKER"), eq("ORDER"), eq(orderId.toString()), any());
  }

  @Test
  void testDispatch_rejectsWhenBotPaused() {
    UUID botId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Bot bot = new Bot(botId, "Alpaca Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, fixedInstant);
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER"));
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testDispatch_rejectsWhenLiveTrading() {
    UUID botId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Bot bot = new Bot(botId, "Live Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.LIVE, BotStatus.RUNNING, fixedInstant);
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertEquals("LIVE_TRADING_DISABLED", ex.getMessage());
    verify(alpacaAdapter, never()).submitOrder(any());
  }

  @Test
  void testDispatch_rejectsWhenOrderAlreadyDispatched() {
    UUID botId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Bot bot = new Bot(botId, "Alpaca Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, fixedInstant);
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-1", botId.toString(), "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"),
        OrderStatus.SUBMITTED, fixedInstant
    );

    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    BrokerAdapterException ex = assertThrows(BrokerAdapterException.class, () -> gateway.dispatch(orderId));
    assertTrue(ex.getMessage().contains("ORDER_NOT_IN_CREATION_STATE"));
  }
}
