package io.algopilot.adapter.bybit;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BybitDemoAdapterTest {
  private BybitConfig config;
  private ObjectMapper json;
  private HttpClient httpClient;
  private HttpResponse<String> httpResponse;
  private BybitDemoAdapter adapter;
  private Instant fixedInstant;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    config = new BybitConfig();
    config.setApiKey("test-bybit-key");
    config.setApiSecret("test-bybit-secret");
    json = new ObjectMapper().findAndRegisterModules();
    httpClient = mock(HttpClient.class);
    httpResponse = mock(HttpResponse.class);
    fixedInstant = Instant.parse("2026-08-31T12:00:00Z");
    Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    adapter = new BybitDemoAdapter(config, json, httpClient, clock);
  }

  @Test
  void testSubmitOrder_successfulBybitOrder() throws Exception {
    String responseJson = """
        {
          "retCode": 0,
          "retMsg": "OK",
          "result": {
            "orderId": "bybit-order-999",
            "orderLinkId": "bybit-client-1"
          }
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    OrderRecord order = new OrderRecord(
        UUID.randomUUID(), "bybit-client-1", "bot-1", "v1", "BTCUSDT",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.5"), new BigDecimal("60000.00"),
        OrderStatus.CREATED, fixedInstant
    );

    OrderSubmissionResult result = adapter.submitOrder(order);

    assertNotNull(result);
    assertEquals("bybit-client-1", result.clientOrderId());
    assertEquals("bybit-order-999", result.exchangeOrderId());
    assertEquals(OrderStatus.SUBMITTED, result.status());
  }

  @Test
  void testCancelOrder_successfulBybitCancel() throws Exception {
    String responseJson = """
        {
          "retCode": 0,
          "retMsg": "OK",
          "result": {
            "orderId": "bybit-order-999"
          }
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    OrderRecord order = new OrderRecord(
        UUID.randomUUID(), "bybit-client-1", "bot-1", "v1", "BTCUSDT",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.5"), new BigDecimal("60000.00"),
        OrderStatus.SUBMITTED, fixedInstant
    );

    OrderCancellationResult result = adapter.cancelOrder(order, "bybit-order-999");
    assertEquals(OrderStatus.CANCEL_REQUESTED, result.status());
    assertEquals("bybit-order-999", result.exchangeOrderId());
  }

  @Test
  void testFetchBalance_successfulNormalization() throws Exception {
    String balanceJson = """
        {
          "retCode": 0,
          "result": {
            "list": [
              {
                "totalEquity": "100000.00",
                "totalWalletBalance": "95000.00",
                "totalAvailableBalance": "80000.00"
              }
            ]
          }
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(balanceJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    BrokerAccountBalance balance = adapter.fetchBalance(Broker.BYBIT_DEMO, ExecutionMode.DEMO);
    assertEquals("USDT", balance.currency());
    assertEquals(new BigDecimal("95000.00"), balance.cash());
    assertEquals(new BigDecimal("80000.00"), balance.buyingPower());
    assertEquals(new BigDecimal("100000.00"), balance.equity());
  }

  @Test
  void testFetchPositions_successfulNormalization() throws Exception {
    String positionsJson = """
        {
          "retCode": 0,
          "result": {
            "list": [
              {
                "symbol": "BTCUSDT",
                "size": "1.0",
                "side": "Buy",
                "avgPrice": "59500.00",
                "unrealisedPnl": "500.00",
                "positionValue": "60000.00"
              }
            ]
          }
        }
        """;
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(positionsJson);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

    List<BrokerPosition> positions = adapter.fetchPositions(Broker.BYBIT_DEMO, ExecutionMode.DEMO, "bot-1");
    assertEquals(1, positions.size());
    assertEquals("BTCUSDT", positions.getFirst().symbol());
    assertEquals(new BigDecimal("1.0"), positions.getFirst().quantity());
    assertEquals(new BigDecimal("59500.00"), positions.getFirst().averageEntryPrice());
  }

  @Test
  void testLiveTrading_strictlyRejected() {
    assertThrows(BrokerAdapterException.class, () -> adapter.fetchBalance(Broker.BYBIT_DEMO, ExecutionMode.LIVE));
  }

  @Test
  void testConfigValidation_rejectsLiveUrl() {
    BybitConfig badConfig = new BybitConfig();
    assertThrows(IllegalStateException.class, () -> badConfig.setBaseUrl("https://api.bybit.com"));
  }
}
