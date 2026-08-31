package io.algopilot.event;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class WebSocketEventPublisherTest {
  private MarketEventBus eventBus;
  private SimpMessagingTemplate messagingTemplate;
  private WebSocketEventPublisher publisher;

  @BeforeEach
  void setUp() {
    eventBus = new MarketEventBus();
    messagingTemplate = mock(SimpMessagingTemplate.class);
    publisher = new WebSocketEventPublisher(eventBus, messagingTemplate);
    publisher.init();
  }

  @Test
  void testBroadcastTick_convertsAndSendsToStompTopics() {
    MarketTick tick = new MarketTick("BTC/USD", new BigDecimal("60000.00"), new BigDecimal("59999.00"), new BigDecimal("60001.00"), BigDecimal.ONE, Instant.now());
    eventBus.publishTick(tick);

    verify(messagingTemplate).convertAndSend(eq("/topic/market-data"), eq(tick));
    verify(messagingTemplate).convertAndSend(eq("/topic/market-data/BTC-USD"), eq(tick));
  }

  @Test
  void testBroadcastSystemEvent_convertsAndSendsToTargetTopic() {
    SystemEvent event = new SystemEvent("bot-status", "BOT_RUNNING", Instant.now(), "bot-1");
    eventBus.publishEvent(event);

    verify(messagingTemplate).convertAndSend(eq("/topic/bot-status"), eq(event));
  }
}
