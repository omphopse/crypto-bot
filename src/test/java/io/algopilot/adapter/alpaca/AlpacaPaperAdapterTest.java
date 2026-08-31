package io.algopilot.adapter.alpaca;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.BrokerAdapterException;
import io.algopilot.adapter.OrderCancellationResult;
import io.algopilot.adapter.OrderSubmissionResult;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerFill;
import io.algopilot.reconciliation.broker.BrokerOrder;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AlpacaPaperAdapterTest {
  private AlpacaConfig config;
  private ObjectMapper json;
  private HttpClient httpClient;
  private HttpResponse<String> httpResponse;
  private AlpacaPaperAdapter adapter;
  private Instant fixedInstant;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    config = new AlpacaConfig();
    config.setKeyId("test-key");
    config.setSecretKey("test-secret");
    json = new ObjectMapper().findAndRegisterModules();
    httpClient = mock(HttpClient.class);
    httpResponse = mock(HttpResponse.class);
    fixedInstant = Instant.parse("2026-08-31T12:00:00Z");
    Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    adapter = new AlpacaPaperAdapter(config, json, httpClient, clock);
  }

  @Test
  void testSubmitOrder_successfulSubmission_returnsMappedResult() throws Exception {
    String responseJson = """
        {
          "id": "alpaca-order-123",
          "client_order_id": "client-123",
          "symbol": "BTCUSD",
          "status": "accepted",
          "qty": "1.5"
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    OrderRecord order = new OrderRecord(
        UUID.randomUUID(), "client-123", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.5"), new BigDecimal("60000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    OrderSubmissionResult result = adapter.submitOrder(order);

    assertNotNull(result);
    assertEquals("client-123", result.clientOrderId());
    assertEquals("alpaca-order-123", result.exchangeOrderId());
    assertEquals(OrderStatus.ACKNOWLEDGED, result.status());
  }

  @Test
  void testCancelOrder_successfulCancellation() throws Exception {
    when(httpResponse.statusCode()).thenReturn(204);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    OrderRecord order = new OrderRecord(
        UUID.randomUUID(), "client-123", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1.5"), new BigDecimal("60000.00"),
        OrderStatus.SUBMITTED, fixedInstant
    );

    OrderCancellationResult result = adapter.cancelOrder(order, "alpaca-order-123");
    assertEquals(OrderStatus.CANCEL_REQUESTED, result.status());
    assertEquals("alpaca-order-123", result.exchangeOrderId());
  }

  @Test
  void testFetchBalance_successfulNormalization() throws Exception {
    String accountJson = """
        {
          "currency": "USD",
          "cash": "25000.50",
          "buying_power": "50000.00",
          "equity": "30000.50"
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(accountJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    BrokerAccountBalance balance = adapter.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.PAPER);
    assertEquals("USD", balance.currency());
    assertEquals(new BigDecimal("25000.50"), balance.cash());
    assertEquals(new BigDecimal("50000.00"), balance.buyingPower());
    assertEquals(new BigDecimal("30000.50"), balance.equity());
  }

  @Test
  void testFetchOpenOrders_successfulNormalization() throws Exception {
    String ordersJson = """
        [
          {
            "id": "brk-order-1",
            "client_order_id": "client-1",
            "symbol": "BTCUSD",
            "side": "buy",
            "qty": "2.0",
            "filled_qty": "0",
            "status": "new",
            "created_at": "2026-08-31T12:00:00Z"
          }
        ]
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(ordersJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    List<BrokerOrder> orders = adapter.fetchOpenOrders(Broker.ALPACA_PAPER, ExecutionMode.PAPER, "bot-1");
    assertEquals(1, orders.size());
    assertEquals("brk-order-1", orders.getFirst().brokerOrderId());
    assertEquals("client-1", orders.getFirst().clientOrderId());
    assertEquals(OrderStatus.ACKNOWLEDGED, orders.getFirst().status());
  }

  @Test
  void testFetchPositions_successfulNormalization() throws Exception {
    String positionsJson = """
        [
          {
            "symbol": "BTCUSD",
            "qty": "1.5",
            "side": "long",
            "avg_entry_price": "60000.00",
            "unrealized_pl": "500.00",
            "market_value": "90500.00"
          }
        ]
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(positionsJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    List<BrokerPosition> positions = adapter.fetchPositions(Broker.ALPACA_PAPER, ExecutionMode.PAPER, "bot-1");
    assertEquals(1, positions.size());
    assertEquals("BTCUSD", positions.getFirst().symbol());
    assertEquals(new BigDecimal("1.5"), positions.getFirst().quantity());
    assertEquals(new BigDecimal("60000.00"), positions.getFirst().averageEntryPrice());
  }

  @Test
  void testLiveTrading_strictlyRejected() {
    assertThrows(BrokerAdapterException.class, () -> adapter.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.LIVE));
  }

  @Test
  void testConfigValidation_rejectsLiveUrl() {
    AlpacaConfig badConfig = new AlpacaConfig();
    assertThrows(IllegalStateException.class, () -> badConfig.setBaseUrl("https://api.alpaca.markets/v2"));
  }
}
