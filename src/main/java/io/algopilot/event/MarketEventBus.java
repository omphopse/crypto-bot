package io.algopilot.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketEventBus {
  private static final Logger log = LoggerFactory.getLogger(MarketEventBus.class);

  private final List<Consumer<MarketTick>> globalTickSubscribers = new CopyOnWriteArrayList<>();
  private final Map<String, List<Consumer<MarketTick>>> symbolTickSubscribers = new ConcurrentHashMap<>();
  private final List<Consumer<SystemEvent>> globalEventSubscribers = new CopyOnWriteArrayList<>();
  private final Map<String, List<Consumer<SystemEvent>>> topicEventSubscribers = new ConcurrentHashMap<>();

  public void publishTick(MarketTick tick) {
    if (tick == null) return;

    for (Consumer<MarketTick> sub : globalTickSubscribers) {
      try {
        sub.accept(tick);
      } catch (Exception e) {
        log.error("Error in global tick subscriber: {}", e.getMessage(), e);
      }
    }

    if (tick.symbol() != null) {
      List<Consumer<MarketTick>> symbolSubs = symbolTickSubscribers.get(tick.symbol().toUpperCase());
      if (symbolSubs != null) {
        for (Consumer<MarketTick> sub : symbolSubs) {
          try {
            sub.accept(tick);
          } catch (Exception e) {
            log.error("Error in symbol tick subscriber for {}: {}", tick.symbol(), e.getMessage(), e);
          }
        }
      }
    }
  }

  public void publishEvent(SystemEvent event) {
    if (event == null) return;

    for (Consumer<SystemEvent> sub : globalEventSubscribers) {
      try {
        sub.accept(event);
      } catch (Exception e) {
        log.error("Error in global event subscriber: {}", e.getMessage(), e);
      }
    }

    if (event.topic() != null) {
      List<Consumer<SystemEvent>> topicSubs = topicEventSubscribers.get(event.topic());
      if (topicSubs != null) {
        for (Consumer<SystemEvent> sub : topicSubs) {
          try {
            sub.accept(event);
          } catch (Exception e) {
            log.error("Error in topic event subscriber for {}: {}", event.topic(), e.getMessage(), e);
          }
        }
      }
    }
  }

  public void subscribeTicks(Consumer<MarketTick> subscriber) {
    globalTickSubscribers.add(subscriber);
  }

  public void subscribeTicks(String symbol, Consumer<MarketTick> subscriber) {
    if (symbol == null) return;
    symbolTickSubscribers.computeIfAbsent(symbol.toUpperCase(), k -> new CopyOnWriteArrayList<>()).add(subscriber);
  }

  public void subscribeEvents(String topic, Consumer<SystemEvent> subscriber) {
    if (topic == null) return;
    topicEventSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriber);
  }

  public void subscribeAllEvents(Consumer<SystemEvent> subscriber) {
    globalEventSubscribers.add(subscriber);
  }

  public void clear() {
    globalTickSubscribers.clear();
    symbolTickSubscribers.clear();
    globalEventSubscribers.clear();
    topicEventSubscribers.clear();
  }
}
