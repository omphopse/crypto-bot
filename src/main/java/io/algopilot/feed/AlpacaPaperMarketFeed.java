package io.algopilot.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.alpaca.AlpacaConfig;
import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlpacaPaperMarketFeed implements MarketDataFeed {
  private static final Logger log = LoggerFactory.getLogger(AlpacaPaperMarketFeed.class);

  private final AlpacaConfig config;
  private final MarketEventBus eventBus;
  private final ObjectMapper json;
  private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
  private volatile boolean connected = false;

  public AlpacaPaperMarketFeed(AlpacaConfig config, MarketEventBus eventBus, ObjectMapper json) {
    this.config = config;
    this.eventBus = eventBus;
    this.json = json;
  }

  @Override
  public Broker getBroker() {
    return Broker.ALPACA_PAPER;
  }

  @Override
  public void subscribe(String symbol) {
    if (symbol != null && !symbol.isBlank()) {
      subscriptions.add(symbol.toUpperCase());
      log.info("Alpaca Paper feed subscribed to {}", symbol);
    }
  }

  @Override
  public void unsubscribe(String symbol) {
    if (symbol != null) {
      subscriptions.remove(symbol.toUpperCase());
      log.info("Alpaca Paper feed unsubscribed from {}", symbol);
    }
  }

  @Override
  public Set<String> getSubscribedSymbols() {
    return Collections.unmodifiableSet(subscriptions);
  }

  @Override
  public void start() {
    connected = true;
    log.info("Alpaca Paper Market Feed started for baseUrl={}", config.getBaseUrl());
  }

  @Override
  public void stop() {
    connected = false;
    log.info("Alpaca Paper Market Feed stopped");
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  public void onMessage(String payload) {
    try {
      JsonNode root = json.readTree(payload);
      if (root.isArray()) {
        for (JsonNode item : root) {
          processMessageItem(item);
        }
      } else {
        processMessageItem(root);
      }
    } catch (Exception e) {
      log.warn("Failed to parse Alpaca market feed message: {}", e.getMessage());
    }
  }

  private void processMessageItem(JsonNode node) {
    String msgType = node.path("T").asText();
    String symbol = node.path("S").asText();

    if ("t".equals(msgType)) { // Trade
      BigDecimal price = new BigDecimal(node.path("p").asText("0")).setScale(4, java.math.RoundingMode.HALF_UP);
      BigDecimal size = new BigDecimal(node.path("s").asText("0"));
      Instant timestamp = node.has("t") ? Instant.parse(node.path("t").asText()) : Instant.now();

      MarketTick tick = new MarketTick(symbol, price, price, price, size, timestamp);
      eventBus.publishTick(tick);
    } else if ("q".equals(msgType)) { // Quote
      BigDecimal bidPrice = new BigDecimal(node.path("bp").asText("0")).setScale(4, java.math.RoundingMode.HALF_UP);
      BigDecimal askPrice = new BigDecimal(node.path("ap").asText("0")).setScale(4, java.math.RoundingMode.HALF_UP);
      BigDecimal midPrice = bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP);
      Instant timestamp = node.has("t") ? Instant.parse(node.path("t").asText()) : Instant.now();

      MarketTick tick = new MarketTick(symbol, midPrice, bidPrice, askPrice, BigDecimal.ZERO, timestamp);
      eventBus.publishTick(tick);
    }
  }
}
