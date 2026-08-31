package io.algopilot.event;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MarketEventBusTest {
  private MarketEventBus eventBus;

  @BeforeEach
  void setUp() {
    eventBus = new MarketEventBus();
  }

  @Test
  void testPublishTick_dispatchesToGlobalAndSymbolSubscribers() {
    List<MarketTick> globalTicks = new ArrayList<>();
    List<MarketTick> btcTicks = new ArrayList<>();
    List<MarketTick> ethTicks = new ArrayList<>();

    eventBus.subscribeTicks(globalTicks::add);
    eventBus.subscribeTicks("BTC/USD", btcTicks::add);
    eventBus.subscribeTicks("ETH/USD", ethTicks::add);

    MarketTick btcTick = new MarketTick("BTC/USD", new BigDecimal("65000.00"), new BigDecimal("64999.00"), new BigDecimal("65001.00"), BigDecimal.ONE, Instant.now());
    MarketTick ethTick = new MarketTick("ETH/USD", new BigDecimal("3500.00"), new BigDecimal("3499.00"), new BigDecimal("3501.00"), BigDecimal.valueOf(2), Instant.now());

    eventBus.publishTick(btcTick);
    eventBus.publishTick(ethTick);

    assertEquals(2, globalTicks.size());
    assertEquals(1, btcTicks.size());
    assertEquals("BTC/USD", btcTicks.get(0).symbol());
    assertEquals(1, ethTicks.size());
    assertEquals("ETH/USD", ethTicks.get(0).symbol());
  }

  @Test
  void testPublishEvent_dispatchesToTopicAndGlobalSubscribers() {
    List<SystemEvent> globalEvents = new ArrayList<>();
    List<SystemEvent> orderEvents = new ArrayList<>();

    eventBus.subscribeAllEvents(globalEvents::add);
    eventBus.subscribeEvents("orders", orderEvents::add);

    SystemEvent orderCreated = new SystemEvent("orders", "ORDER_CREATED", Instant.now(), "ORD-123");
    SystemEvent botPaused = new SystemEvent("bots", "BOT_PAUSED", Instant.now(), "BOT-456");

    eventBus.publishEvent(orderCreated);
    eventBus.publishEvent(botPaused);

    assertEquals(2, globalEvents.size());
    assertEquals(1, orderEvents.size());
    assertEquals("ORDER_CREATED", orderEvents.get(0).eventType());
  }
}
