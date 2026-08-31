package io.algopilot.event;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublisher.class);

  private final MarketEventBus eventBus;
  private final SimpMessagingTemplate messagingTemplate;

  public WebSocketEventPublisher(MarketEventBus eventBus, SimpMessagingTemplate messagingTemplate) {
    this.eventBus = eventBus;
    this.messagingTemplate = messagingTemplate;
  }

  @PostConstruct
  public void init() {
    eventBus.subscribeTicks(this::broadcastTick);
    eventBus.subscribeAllEvents(this::broadcastSystemEvent);
  }

  public void broadcastTick(MarketTick tick) {
    if (tick == null) return;
    try {
      // Broadcast to general market data topic and symbol-specific topic
      messagingTemplate.convertAndSend("/topic/market-data", tick);
      if (tick.symbol() != null) {
        String cleanSymbol = tick.symbol().replace("/", "-").toUpperCase();
        messagingTemplate.convertAndSend("/topic/market-data/" + cleanSymbol, tick);
      }
    } catch (Exception e) {
      log.warn("Failed to broadcast market tick via WebSocket: {}", e.getMessage());
    }
  }

  public void broadcastSystemEvent(SystemEvent event) {
    if (event == null || event.topic() == null) return;
    try {
      String destination = event.topic().startsWith("/topic/") ? event.topic() : "/topic/" + event.topic();
      messagingTemplate.convertAndSend(destination, event);
    } catch (Exception e) {
      log.warn("Failed to broadcast system event {} to {}: {}", event.eventType(), event.topic(), e.getMessage());
    }
  }
}
