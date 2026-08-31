package io.algopilot.feed;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.adapter.bybit.BybitConfig;
import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BybitDemoMarketFeedTest {
  private BybitConfig config;
  private MarketEventBus eventBus;
  private ObjectMapper json;
  private BybitDemoMarketFeed feed;
  private List<MarketTick> publishedTicks;

  @BeforeEach
  void setUp() {
    config = new BybitConfig("test-key", "test-secret", "https://api-demo.bybit.com");
    eventBus = new MarketEventBus();
    json = new ObjectMapper();
    feed = new BybitDemoMarketFeed(config, eventBus, json);

    publishedTicks = new ArrayList<>();
    eventBus.subscribeTicks(publishedTicks::add);
  }

  @Test
  void testMetadata_andLifecycle() {
    assertEquals(Broker.BYBIT_DEMO, feed.getBroker());
    assertFalse(feed.isConnected());
    feed.start();
    assertTrue(feed.isConnected());
    feed.subscribe("BTCUSDT");
    assertTrue(feed.getSubscribedSymbols().contains("BTCUSDT"));
    feed.unsubscribe("BTCUSDT");
    assertFalse(feed.getSubscribedSymbols().contains("BTCUSDT"));
    feed.stop();
    assertFalse(feed.isConnected());
  }

  @Test
  void testOnMessage_parsesTickerMessage() {
    String payload = """
        {
          "topic": "tickers.BTCUSDT",
          "ts": 1690000000000,
          "data": {
            "symbol": "BTCUSDT",
            "lastPrice": "58250.00",
            "bid1Price": "58249.50",
            "ask1Price": "58250.50",
            "volume24h": "9876.54"
          }
        }
        """;
    feed.onMessage(payload);

    assertEquals(1, publishedTicks.size());
    MarketTick tick = publishedTicks.get(0);
    assertEquals("BTCUSDT", tick.symbol());
    assertEquals(new BigDecimal("58250.0000"), tick.price());
    assertEquals(new BigDecimal("58249.5000"), tick.bid());
    assertEquals(new BigDecimal("58250.5000"), tick.ask());
    assertEquals(new BigDecimal("9876.54"), tick.volume());
  }
}
