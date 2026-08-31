package io.algopilot.feed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class FeedControllerTest {
  private MarketDataFeed mockFeed;
  private MarketEventBus mockEventBus;
  private FeedController controller;

  @BeforeEach
  void setUp() {
    mockFeed = mock(MarketDataFeed.class);
    when(mockFeed.getBroker()).thenReturn(Broker.ALPACA_PAPER);
    when(mockFeed.isConnected()).thenReturn(true);
    when(mockFeed.getSubscribedSymbols()).thenReturn(Set.of("BTC/USD"));

    mockEventBus = mock(MarketEventBus.class);
    controller = new FeedController(List.of(mockFeed), mockEventBus);
  }

  @Test
  void testGetFeedStatuses() {
    var list = controller.getFeedStatuses();
    assertEquals(1, list.size());
    assertEquals(Broker.ALPACA_PAPER, list.get(0).broker());
    assertTrue(list.get(0).connected());
    assertTrue(list.get(0).subscribedSymbols().contains("BTC/USD"));
  }

  @Test
  void testSubscribe() {
    ResponseEntity<?> response = controller.subscribe(new FeedController.SubscribeRequest(Broker.ALPACA_PAPER, "ETH/USD"));
    assertEquals(200, response.getStatusCode().value());
    verify(mockFeed).subscribe("ETH/USD");
  }

  @Test
  void testPublishTick() {
    ResponseEntity<?> response = controller.publishTick(new FeedController.PublishTickRequest("BTC/USD", new BigDecimal("60000"), null, null, null));
    assertEquals(200, response.getStatusCode().value());
    verify(mockEventBus).publishTick(any(MarketTick.class));
  }
}
