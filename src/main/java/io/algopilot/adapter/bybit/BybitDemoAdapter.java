package io.algopilot.adapter.bybit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.BrokerAdapterException;
import io.algopilot.adapter.BrokerOrderAdapter;
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
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.risk.RiskDecisionRequest.Side;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BybitDemoAdapter implements BrokerOrderAdapter, BrokerStateProvider {
  private static final Logger log = LoggerFactory.getLogger(BybitDemoAdapter.class);

  private final BybitConfig config;
  private final ObjectMapper json;
  private final HttpClient httpClient;
  private final Clock clock;

  public BybitDemoAdapter(BybitConfig config, ObjectMapper json) {
    this(config, json, HttpClient.newHttpClient(), Clock.systemUTC());
  }

  public BybitDemoAdapter(BybitConfig config, ObjectMapper json, HttpClient httpClient, Clock clock) {
    this.config = config;
    this.json = json;
    this.httpClient = httpClient;
    this.clock = clock;
    config.validate();
  }

  @Override
  public Broker broker() {
    return Broker.BYBIT_DEMO;
  }

  @Override
  public ExecutionMode supportedMode() {
    return ExecutionMode.DEMO;
  }

  @Override
  public OrderSubmissionResult submitOrder(OrderRecord order) {
    validateOrder(order);

    Map<String, Object> payload = Map.of(
        "category", "linear",
        "symbol", normalizeSymbol(order.symbol()),
        "side", order.side() == Side.BUY ? "Buy" : "Sell",
        "orderType", "Market",
        "qty", order.quantity().toPlainString(),
        "orderLinkId", order.clientOrderId()
    );

    try {
      String requestBody = json.writeValueAsString(payload);
      HttpRequest request = buildSignedRequest("/v5/order/create", "POST", requestBody);
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new BrokerAdapterException("BYBIT_ORDER_SUBMISSION_FAILED: HTTP " + response.statusCode() + " - " + response.body());
      }

      JsonNode root = json.readTree(response.body());
      int retCode = root.path("retCode").asInt(-1);
      if (retCode != 0) {
        throw new BrokerAdapterException("BYBIT_API_ERROR: code=" + retCode + " msg=" + root.path("retMsg").asText());
      }

      JsonNode result = root.path("result");
      String exchangeOrderId = result.path("orderId").asText("");
      Map<String, Object> details = json.convertValue(result, new TypeReference<Map<String, Object>>() {});

      return new OrderSubmissionResult(order.clientOrderId(), exchangeOrderId, OrderStatus.SUBMITTED, clock.instant(), details);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new BrokerAdapterException("BYBIT_SUBMISSION_COMMUNICATION_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public OrderCancellationResult cancelOrder(OrderRecord order, String exchangeOrderId) {
    try {
      Map<String, Object> payload = Map.of(
          "category", "linear",
          "symbol", normalizeSymbol(order.symbol()),
          "orderId", exchangeOrderId != null ? exchangeOrderId : "",
          "orderLinkId", order.clientOrderId()
      );
      String requestBody = json.writeValueAsString(payload);
      HttpRequest request = buildSignedRequest("/v5/order/cancel", "POST", requestBody);
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("BYBIT_CANCELLATION_FAILED: HTTP " + response.statusCode());
      }

      return new OrderCancellationResult(order.clientOrderId(), exchangeOrderId, OrderStatus.CANCEL_REQUESTED, clock.instant(), "Bybit cancel requested");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new BrokerAdapterException("BYBIT_CANCELLATION_COMMUNICATION_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public Optional<BrokerOrder> getOrderStatus(String clientOrderId, String exchangeOrderId) {
    try {
      String query = "/v5/order/realtime?category=linear"
          + (exchangeOrderId != null && !exchangeOrderId.isBlank() ? "&orderId=" + exchangeOrderId : "&orderLinkId=" + clientOrderId);
      HttpRequest request = buildSignedRequest(query, "GET", "");
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) return Optional.empty();

      JsonNode root = json.readTree(response.body());
      JsonNode listNode = root.path("result").path("list");
      if (listNode.isArray() && !listNode.isEmpty()) {
        return Optional.of(mapNodeToBrokerOrder(listNode.get(0), ""));
      }
      return Optional.empty();
    } catch (Exception e) {
      throw new BrokerAdapterException("BYBIT_GET_STATUS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public BrokerAccountBalance fetchBalance(Broker broker, ExecutionMode mode) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildSignedRequest("/v5/account/wallet-balance?accountType=UNIFIED", "GET", "");
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("BYBIT_BALANCE_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode root = json.readTree(response.body());
      JsonNode listNode = root.path("result").path("list");
      BigDecimal equity = BigDecimal.ZERO;
      BigDecimal walletBalance = BigDecimal.ZERO;
      BigDecimal available = BigDecimal.ZERO;

      if (listNode.isArray() && !listNode.isEmpty()) {
        JsonNode acc = listNode.get(0);
        equity = new BigDecimal(acc.path("totalEquity").asText("0"));
        walletBalance = new BigDecimal(acc.path("totalWalletBalance").asText("0"));
        available = new BigDecimal(acc.path("totalAvailableBalance").asText("0"));
      }

      return new BrokerAccountBalance("USDT", walletBalance, available, equity, clock.instant());
    } catch (Exception e) {
      throw new BrokerAdapterException("BYBIT_FETCH_BALANCE_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerOrder> fetchOpenOrders(Broker broker, ExecutionMode mode, String botId) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildSignedRequest("/v5/order/realtime?category=linear", "GET", "");
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("BYBIT_OPEN_ORDERS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode root = json.readTree(response.body());
      JsonNode listNode = root.path("result").path("list");
      List<BrokerOrder> list = new ArrayList<>();
      if (listNode.isArray()) {
        for (JsonNode n : listNode) {
          list.add(mapNodeToBrokerOrder(n, botId));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("BYBIT_FETCH_OPEN_ORDERS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerFill> fetchFills(Broker broker, ExecutionMode mode, String botId, Instant since) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildSignedRequest("/v5/execution/list?category=linear", "GET", "");
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("BYBIT_FILLS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode root = json.readTree(response.body());
      JsonNode listNode = root.path("result").path("list");
      List<BrokerFill> list = new ArrayList<>();
      if (listNode.isArray()) {
        for (JsonNode n : listNode) {
          String execId = n.path("execId").asText();
          String orderId = n.path("orderId").asText();
          String orderLinkId = n.path("orderLinkId").asText();
          String symbol = n.path("symbol").asText();
          String sideStr = n.path("side").asText("Buy");
          BigDecimal qty = new BigDecimal(n.path("execQty").asText("0"));
          BigDecimal price = new BigDecimal(n.path("execPrice").asText("0"));
          BigDecimal fee = new BigDecimal(n.path("execFee").asText("0"));
          long execTimeMs = n.path("execTime").asLong(clock.millis());

          list.add(new BrokerFill(
              execId, orderId, orderLinkId, botId, symbol,
              "Buy".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL,
              qty, price, fee, Instant.ofEpochMilli(execTimeMs)
          ));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("BYBIT_FETCH_FILLS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerPosition> fetchPositions(Broker broker, ExecutionMode mode, String botId) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildSignedRequest("/v5/position/list?category=linear&settleCoin=USDT", "GET", "");
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("BYBIT_POSITIONS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode root = json.readTree(response.body());
      JsonNode listNode = root.path("result").path("list");
      List<BrokerPosition> list = new ArrayList<>();
      if (listNode.isArray()) {
        for (JsonNode n : listNode) {
          String symbol = n.path("symbol").asText();
          BigDecimal size = new BigDecimal(n.path("size").asText("0"));
          String side = n.path("side").asText("None");
          if ("Sell".equalsIgnoreCase(side) && size.signum() > 0) {
            size = size.negate();
          }
          BigDecimal avgPrice = new BigDecimal(n.path("avgPrice").asText("0"));
          BigDecimal unrealisedPnl = new BigDecimal(n.path("unrealisedPnl").asText("0"));
          BigDecimal positionValue = new BigDecimal(n.path("positionValue").asText("0"));

          list.add(new BrokerPosition(botId, symbol, size, avgPrice, unrealisedPnl, positionValue, clock.instant()));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("BYBIT_FETCH_POSITIONS_ERROR: " + e.getMessage(), e);
    }
  }

  private HttpRequest buildSignedRequest(String pathAndQuery, String method, String body) {
    long timestamp = clock.millis();
    String recvWindow = config.getRecvWindow();
    String payloadToSign = timestamp + config.getApiKey() + recvWindow + ("GET".equalsIgnoreCase(method) ? "" : body);
    String signature = generateHmacSha256(payloadToSign, config.getApiSecret());

    String uri = config.getBaseUrl() + pathAndQuery;
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header("X-BAPI-API-KEY", config.getApiKey())
        .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
        .header("X-BAPI-RECV-WINDOW", recvWindow)
        .header("X-BAPI-SIGN", signature)
        .header("Content-Type", "application/json");

    if ("POST".equalsIgnoreCase(method)) {
      builder.POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      builder.GET();
    }

    return builder.build();
  }

  private String generateHmacSha256(String data, String secret) {
    if (secret == null || secret.isBlank()) return "";
    try {
      Mac sha256 = Mac.getInstance("HmacSHA256");
      SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      sha256.init(keySpec);
      byte[] hash = sha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
    }
  }

  private BrokerOrder mapNodeToBrokerOrder(JsonNode n, String botId) {
    String brokerOrderId = n.path("orderId").asText("");
    String clientOrderId = n.path("orderLinkId").asText("");
    String symbol = n.path("symbol").asText("");
    String sideStr = n.path("side").asText("Buy");
    BigDecimal qty = new BigDecimal(n.path("qty").asText("0"));
    BigDecimal cumExecQty = new BigDecimal(n.path("cumExecQty").asText("0"));
    BigDecimal price = n.hasNonNull("price") ? new BigDecimal(n.path("price").asText("0")) : BigDecimal.ZERO;
    OrderStatus status = mapBybitOrderStatus(n.path("orderStatus").asText(""));
    long createdTimeMs = n.path("createdTime").asLong(clock.millis());

    return new BrokerOrder(
        brokerOrderId, clientOrderId, botId, symbol,
        "Buy".equalsIgnoreCase(sideStr) ? Side.BUY : Side.SELL,
        qty, cumExecQty, price, status, Instant.ofEpochMilli(createdTimeMs), clock.instant()
    );
  }

  private OrderStatus mapBybitOrderStatus(String bybitStatus) {
    return switch (bybitStatus) {
      case "New", "Untriggered" -> OrderStatus.ACKNOWLEDGED;
      case "PartiallyFilled" -> OrderStatus.PARTIALLY_FILLED;
      case "Filled" -> OrderStatus.FILLED;
      case "Cancelled" -> OrderStatus.CANCELLED;
      case "Rejected" -> OrderStatus.REJECTED;
      case "Deactivated" -> OrderStatus.EXPIRED;
      default -> OrderStatus.SUBMITTED;
    };
  }

  private String normalizeSymbol(String symbol) {
    return symbol.replace("/", "").replace("-", "");
  }

  private void validateOrder(OrderRecord order) {
    if (order.quantity() == null || order.quantity().signum() <= 0) {
      throw new BrokerAdapterException("INVALID_ORDER_QUANTITY");
    }
    if (order.clientOrderId() == null || order.clientOrderId().isBlank()) {
      throw new BrokerAdapterException("CLIENT_ORDER_ID_REQUIRED");
    }
  }

  private void checkCompatibility(Broker broker, ExecutionMode mode) {
    if (mode == ExecutionMode.LIVE) {
      throw new BrokerAdapterException("LIVE_TRADING_DISABLED");
    }
    if (broker != Broker.BYBIT_DEMO || mode != ExecutionMode.DEMO) {
      throw new BrokerAdapterException("BROKER_MODE_MISMATCH: Bybit adapter only supports BYBIT_DEMO / DEMO");
    }
  }
}
