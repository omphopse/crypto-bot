package io.algopilot.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.bybit.BybitConfig;
import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BybitDemoMarketFeed implements MarketDataFeed {
  private static final Logger log = LoggerFactory.getLogger(BybitDemoMarketFeed.class);

  private final BybitConfig config;
  private final MarketEventBus eventBus;
  private final ObjectMapper json;
  private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
  private volatile boolean connected = false;

  public BybitDemoMarketFeed(BybitConfig config, MarketEventBus eventBus, ObjectMapper json) {
    this.config = config;
    this.eventBus = eventBus;
    this.json = json;
  }

  @Override
  public Broker getBroker() {
    return Broker.BYBIT_DEMO;
  }

  @Override
  public void subscribe(String symbol) {
    if (symbol != null && !symbol.isBlank()) {
      subscriptions.add(symbol.toUpperCase());
      log.info("Bybit Demo feed subscribed to {}", symbol);
    }
  }

  @Override
  public void unsubscribe(String symbol) {
    if (symbol != null) {
      subscriptions.remove(symbol.toUpperCase());
      log.info("Bybit Demo feed unsubscribed from {}", symbol);
    }
  }

  @Override
  public Set<String> getSubscribedSymbols() {
    return Collections.unmodifiableSet(subscriptions);
  }

  @Override
  public void start() {
    connected = true;
    log.info("Bybit Demo Market Feed started for baseUrl={}", config.getBaseUrl());
  }

  @Override
  public void stop() {
    connected = false;
    log.info("Bybit Demo Market Feed stopped");
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  public void onMessage(String payload) {
    try {
      JsonNode root = json.readTree(payload);
      String topic = root.path("topic").asText();

      if (topic.startsWith("tickers.") || topic.startsWith("publicTrade.")) {
        JsonNode data = root.path("data");
        if (data.isArray()) {
          for (JsonNode item : data) {
            processItem(item, root.path("ts").asLong(System.currentTimeMillis()));
          }
        } else if (data.isObject()) {
          processItem(data, root.path("ts").asLong(System.currentTimeMillis()));
        }
      }
    } catch (Exception e) {
      log.warn("Failed to parse Bybit market feed message: {}", e.getMessage());
    }
  }

  private void processItem(JsonNode node, long ts) {
    String symbol = node.path("symbol").asText(node.path("s").asText());
    if (symbol == null || symbol.isBlank()) return;

    BigDecimal price = node.has("lastPrice")
        ? new BigDecimal(node.path("lastPrice").asText("0"))
        : new BigDecimal(node.path("p").asText("0"));

    BigDecimal bid = node.has("bid1Price") ? new BigDecimal(node.path("bid1Price").asText("0")) : price;
    BigDecimal ask = node.has("ask1Price") ? new BigDecimal(node.path("ask1Price").asText("0")) : price;
    BigDecimal volume = node.has("volume24h")
        ? new BigDecimal(node.path("volume24h").asText("0"))
        : new BigDecimal(node.path("v").asText("0"));

    Instant timestamp = ts > 0 ? Instant.ofEpochMilli(ts) : Instant.now();

    MarketTick tick = new MarketTick(symbol, price.setScale(4, RoundingMode.HALF_UP), bid.setScale(4, RoundingMode.HALF_UP), ask.setScale(4, RoundingMode.HALF_UP), volume, timestamp);
    eventBus.publishTick(tick);
  }
}
