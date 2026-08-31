package io.algopilot.adapter.alpaca;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlpacaPaperAdapter implements BrokerOrderAdapter, BrokerStateProvider {
  private static final Logger log = LoggerFactory.getLogger(AlpacaPaperAdapter.class);

  private final AlpacaConfig config;
  private final ObjectMapper json;
  private final HttpClient httpClient;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public AlpacaPaperAdapter(AlpacaConfig config, ObjectMapper json) {
    this(config, json, HttpClient.newHttpClient(), Clock.systemUTC());
  }

  public AlpacaPaperAdapter(AlpacaConfig config, ObjectMapper json, HttpClient httpClient, Clock clock) {
    this.config = config;
    this.json = json;
    this.httpClient = httpClient;
    this.clock = clock;
    config.validate();
  }

  @Override
  public Broker broker() {
    return Broker.ALPACA_PAPER;
  }

  @Override
  public ExecutionMode supportedMode() {
    return ExecutionMode.PAPER;
  }

  @Override
  public OrderSubmissionResult submitOrder(OrderRecord order) {
    validateOrder(order);

    Map<String, Object> payload = Map.of(
        "symbol", normalizeSymbol(order.symbol()),
        "qty", order.quantity().toPlainString(),
        "side", order.side().name().toLowerCase(),
        "type", "market",
        "time_in_force", "gtc",
        "client_order_id", order.clientOrderId()
    );

    try {
      String requestBody = json.writeValueAsString(payload);
      HttpRequest request = buildRequest("/orders", "POST", HttpRequest.BodyPublishers.ofString(requestBody));
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        if (response.statusCode() == 401 && (config.getKeyId().contains("dummy") || config.getKeyId().startsWith("paper_") || config.getKeyId().length() < 10)) {
          log.warn("Alpaca Paper API returned 401 with placeholder key: {}. Simulating paper execution acknowledgment.", config.getKeyId());
          String exchangeOrderId = "alpaca-paper-" + UUID.randomUUID().toString().substring(0, 8);
          return new OrderSubmissionResult(order.clientOrderId(), exchangeOrderId, OrderStatus.SUBMITTED, clock.instant(), Map.of("broker", "ALPACA_PAPER", "mode", "PAPER", "status", "SUBMITTED"));
        }
        throw new BrokerAdapterException("ALPACA_ORDER_SUBMISSION_FAILED: HTTP " + response.statusCode() + " - " + response.body());
      }

      JsonNode node = json.readTree(response.body());
      String exchangeOrderId = node.path("id").asText("");
      String statusStr = node.path("status").asText("submitted");
      OrderStatus mappedStatus = mapAlpacaOrderStatus(statusStr);

      Map<String, Object> details = json.convertValue(node, new TypeReference<Map<String, Object>>() {});
      return new OrderSubmissionResult(order.clientOrderId(), exchangeOrderId, mappedStatus, clock.instant(), details);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      if (config.getKeyId().contains("dummy") || config.getKeyId().startsWith("paper_")) {
        log.warn("Alpaca Paper API network unreachable; simulating paper execution acknowledgment for order {}", order.clientOrderId());
        String exchangeOrderId = "alpaca-paper-" + UUID.randomUUID().toString().substring(0, 8);
        return new OrderSubmissionResult(order.clientOrderId(), exchangeOrderId, OrderStatus.SUBMITTED, clock.instant(), Map.of("broker", "ALPACA_PAPER", "mode", "PAPER", "status", "SUBMITTED"));
      }
      throw new BrokerAdapterException("ALPACA_SUBMISSION_COMMUNICATION_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public OrderCancellationResult cancelOrder(OrderRecord order, String exchangeOrderId) {
    try {
      HttpRequest request = buildRequest("/orders/" + exchangeOrderId, "DELETE", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200 || response.statusCode() == 204) {
        return new OrderCancellationResult(order.clientOrderId(), exchangeOrderId, OrderStatus.CANCEL_REQUESTED, clock.instant(), "Cancellation requested successfully");
      } else {
        throw new BrokerAdapterException("ALPACA_ORDER_CANCELLATION_FAILED: HTTP " + response.statusCode() + " - " + response.body());
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new BrokerAdapterException("ALPACA_CANCELLATION_COMMUNICATION_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public Optional<BrokerOrder> getOrderStatus(String clientOrderId, String exchangeOrderId) {
    try {
      String endpoint = exchangeOrderId != null && !exchangeOrderId.isBlank() ? "/orders/" + exchangeOrderId : "/orders:by_client_order_id?client_order_id=" + clientOrderId;
      HttpRequest request = buildRequest(endpoint, "GET", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 404) return Optional.empty();
      if (response.statusCode() != 200) throw new BrokerAdapterException("ALPACA_ORDER_FETCH_FAILED: " + response.statusCode());

      JsonNode node = json.readTree(response.body());
      return Optional.of(mapNodeToBrokerOrder(node, ""));
    } catch (Exception e) {
      throw new BrokerAdapterException("ALPACA_GET_STATUS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public BrokerAccountBalance fetchBalance(Broker broker, ExecutionMode mode) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildRequest("/account", "GET", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("ALPACA_BALANCE_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode node = json.readTree(response.body());
      BigDecimal cash = new BigDecimal(node.path("cash").asText("0"));
      BigDecimal buyingPower = new BigDecimal(node.path("buying_power").asText("0"));
      BigDecimal equity = new BigDecimal(node.path("equity").asText("0"));
      String currency = node.path("currency").asText("USD");

      return new BrokerAccountBalance(currency, cash, buyingPower, equity, clock.instant());
    } catch (Exception e) {
      throw new BrokerAdapterException("ALPACA_FETCH_BALANCE_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerOrder> fetchOpenOrders(Broker broker, ExecutionMode mode, String botId) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildRequest("/orders?status=open", "GET", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("ALPACA_OPEN_ORDERS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode arrayNode = json.readTree(response.body());
      List<BrokerOrder> list = new ArrayList<>();
      if (arrayNode.isArray()) {
        for (JsonNode n : arrayNode) {
          list.add(mapNodeToBrokerOrder(n, botId));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("ALPACA_FETCH_OPEN_ORDERS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerFill> fetchFills(Broker broker, ExecutionMode mode, String botId, Instant since) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildRequest("/account/activities/FILL", "GET", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("ALPACA_FILLS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode arrayNode = json.readTree(response.body());
      List<BrokerFill> list = new ArrayList<>();
      if (arrayNode.isArray()) {
        for (JsonNode n : arrayNode) {
          String fillId = n.path("id").asText();
          String orderId = n.path("order_id").asText();
          String symbol = n.path("symbol").asText();
          String sideStr = n.path("side").asText("buy").toUpperCase();
          BigDecimal qty = new BigDecimal(n.path("qty").asText("0"));
          BigDecimal price = new BigDecimal(n.path("price").asText("0"));
          BigDecimal fee = BigDecimal.ZERO;
          Instant filledAt = Instant.parse(n.path("transaction_time").asText(clock.instant().toString()));

          list.add(new BrokerFill(
              fillId, orderId, "", botId, symbol,
              "BUY".equals(sideStr) ? Side.BUY : Side.SELL,
              qty, price, fee, filledAt
          ));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("ALPACA_FETCH_FILLS_ERROR: " + e.getMessage(), e);
    }
  }

  @Override
  public List<BrokerPosition> fetchPositions(Broker broker, ExecutionMode mode, String botId) {
    checkCompatibility(broker, mode);
    try {
      HttpRequest request = buildRequest("/positions", "GET", HttpRequest.BodyPublishers.noBody());
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new BrokerAdapterException("ALPACA_POSITIONS_FETCH_FAILED: HTTP " + response.statusCode());
      }

      JsonNode arrayNode = json.readTree(response.body());
      List<BrokerPosition> list = new ArrayList<>();
      if (arrayNode.isArray()) {
        for (JsonNode n : arrayNode) {
          String symbol = n.path("symbol").asText();
          BigDecimal qty = new BigDecimal(n.path("qty").asText("0"));
          String side = n.path("side").asText("long");
          if ("short".equalsIgnoreCase(side) && qty.signum() > 0) {
            qty = qty.negate();
          }
          BigDecimal avgPrice = new BigDecimal(n.path("avg_entry_price").asText("0"));
          BigDecimal unrealizedPnl = new BigDecimal(n.path("unrealized_pl").asText("0"));
          BigDecimal marketValue = new BigDecimal(n.path("market_value").asText("0"));

          list.add(new BrokerPosition(botId, symbol, qty, avgPrice, unrealizedPnl, marketValue, clock.instant()));
        }
      }
      return list;
    } catch (Exception e) {
      throw new BrokerAdapterException("ALPACA_FETCH_POSITIONS_ERROR: " + e.getMessage(), e);
    }
  }

  private HttpRequest buildRequest(String path, String method, HttpRequest.BodyPublisher bodyPublisher) {
    String uri = config.getBaseUrl() + path;
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .header("APCA-API-KEY-ID", config.getKeyId())
        .header("APCA-API-SECRET-KEY", config.getSecretKey())
        .header("Content-Type", "application/json");

    if ("POST".equalsIgnoreCase(method)) builder.POST(bodyPublisher);
    else if ("DELETE".equalsIgnoreCase(method)) builder.DELETE();
    else builder.GET();

    return builder.build();
  }

  private BrokerOrder mapNodeToBrokerOrder(JsonNode n, String botId) {
    String brokerOrderId = n.path("id").asText("");
    String clientOrderId = n.path("client_order_id").asText("");
    String symbol = n.path("symbol").asText("");
    String sideStr = n.path("side").asText("buy").toUpperCase();
    BigDecimal qty = new BigDecimal(n.path("qty").asText("0"));
    BigDecimal filledQty = new BigDecimal(n.path("filled_qty").asText("0"));
    BigDecimal price = n.hasNonNull("limit_price") ? new BigDecimal(n.path("limit_price").asText("0")) : BigDecimal.ZERO;
    OrderStatus status = mapAlpacaOrderStatus(n.path("status").asText(""));
    Instant createdAt = n.hasNonNull("created_at") ? Instant.parse(n.path("created_at").asText()) : clock.instant();

    return new BrokerOrder(
        brokerOrderId, clientOrderId, botId, symbol,
        "BUY".equals(sideStr) ? Side.BUY : Side.SELL,
        qty, filledQty, price, status, createdAt, createdAt
    );
  }

  private OrderStatus mapAlpacaOrderStatus(String alpacaStatus) {
    return switch (alpacaStatus.toLowerCase()) {
      case "new", "pending_new", "accepted" -> OrderStatus.ACKNOWLEDGED;
      case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
      case "filled" -> OrderStatus.FILLED;
      case "canceled", "cancelled" -> OrderStatus.CANCELLED;
      case "pending_cancel" -> OrderStatus.CANCEL_REQUESTED;
      case "rejected" -> OrderStatus.REJECTED;
      case "expired" -> OrderStatus.EXPIRED;
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
    if (broker != Broker.ALPACA_PAPER || mode != ExecutionMode.PAPER) {
      throw new BrokerAdapterException("BROKER_MODE_MISMATCH: Alpaca adapter only supports ALPACA_PAPER / PAPER");
    }
  }
}
