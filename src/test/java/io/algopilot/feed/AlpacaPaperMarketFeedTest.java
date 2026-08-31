package io.algopilot.feed;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.alpaca.AlpacaConfig;
import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AlpacaPaperMarketFeedTest {
  private AlpacaConfig config;
  private MarketEventBus eventBus;
  private ObjectMapper json;
  private AlpacaPaperMarketFeed feed;
  private List<MarketTick> publishedTicks;

  @BeforeEach
  void setUp() {
    config = new AlpacaConfig("test-key", "test-secret", "https://paper-api.alpaca.markets/v2");
    eventBus = new MarketEventBus();
    json = new ObjectMapper();
    feed = new AlpacaPaperMarketFeed(config, eventBus, json);

    publishedTicks = new ArrayList<>();
    eventBus.subscribeTicks(publishedTicks::add);
  }

  @Test
  void testMetadata_andLifecycle() {
    assertEquals(Broker.ALPACA_PAPER, feed.getBroker());
    assertFalse(feed.isConnected());
    feed.start();
    assertTrue(feed.isConnected());
    feed.subscribe("BTC/USD");
    assertTrue(feed.getSubscribedSymbols().contains("BTC/USD"));
    feed.unsubscribe("BTC/USD");
    assertFalse(feed.getSubscribedSymbols().contains("BTC/USD"));
    feed.stop();
    assertFalse(feed.isConnected());
  }

  @Test
  void testOnMessage_parsesTradeMessage() {
    String payload = """
        [
          {"T":"t","S":"BTC/USD","p":62500.50,"s":0.75,"t":"2026-08-31T12:00:00Z"}
        ]
        """;
    feed.onMessage(payload);

    assertEquals(1, publishedTicks.size());
    MarketTick tick = publishedTicks.get(0);
    assertEquals("BTC/USD", tick.symbol());
    assertEquals(new BigDecimal("62500.5000"), tick.price());
    assertEquals(new BigDecimal("0.75"), tick.volume());
  }

  @Test
  void testOnMessage_parsesQuoteMessage() {
    String payload = """
        [
          {"T":"q","S":"ETH/USD","bp":3100.00,"ap":3102.00,"t":"2026-08-31T12:00:00Z"}
        ]
        """;
    feed.onMessage(payload);

    assertEquals(1, publishedTicks.size());
    MarketTick tick = publishedTicks.get(0);
    assertEquals("ETH/USD", tick.symbol());
    assertEquals(new BigDecimal("3101.0000"), tick.price());
    assertEquals(new BigDecimal("3100.0000"), tick.bid());
    assertEquals(new BigDecimal("3102.0000"), tick.ask());
  }
}
