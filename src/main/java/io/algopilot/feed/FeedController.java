package io.algopilot.feed;

import io.algopilot.bot.Broker;
import io.algopilot.event.MarketEventBus;
import io.algopilot.event.MarketTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
  private final List<MarketDataFeed> feeds;
  private final MarketEventBus eventBus;

  public record FeedStatusDto(Broker broker, boolean connected, Set<String> subscribedSymbols) {}

  public record SubscribeRequest(Broker broker, String symbol) {}

  public record PublishTickRequest(String symbol, BigDecimal price, BigDecimal bid, BigDecimal ask, BigDecimal volume) {}

  public FeedController(List<MarketDataFeed> feeds, MarketEventBus eventBus) {
    this.feeds = feeds;
    this.eventBus = eventBus;
  }

  @GetMapping("/status")
  public List<FeedStatusDto> getFeedStatuses() {
    return feeds.stream()
        .map(f -> new FeedStatusDto(f.getBroker(), f.isConnected(), f.getSubscribedSymbols()))
        .toList();
  }

  @PostMapping("/subscribe")
  public ResponseEntity<?> subscribe(@RequestBody SubscribeRequest req) {
    if (req.broker() == null || req.symbol() == null || req.symbol().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "BROKER_AND_SYMBOL_REQUIRED"));
    }

    for (MarketDataFeed feed : feeds) {
      if (feed.getBroker() == req.broker()) {
        feed.subscribe(req.symbol());
        return ResponseEntity.ok(Map.of("status", "SUBSCRIBED", "broker", req.broker().name(), "symbol", req.symbol()));
      }
    }
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/publish")
  public ResponseEntity<?> publishTick(@RequestBody PublishTickRequest req) {
    if (req.symbol() == null || req.price() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "SYMBOL_AND_PRICE_REQUIRED"));
    }
    BigDecimal bid = req.bid() != null ? req.bid() : req.price();
    BigDecimal ask = req.ask() != null ? req.ask() : req.price();
    BigDecimal vol = req.volume() != null ? req.volume() : BigDecimal.ONE;

    MarketTick tick = new MarketTick(req.symbol(), req.price(), bid, ask, vol, Instant.now());
    eventBus.publishTick(tick);
    return ResponseEntity.ok(Map.of("status", "PUBLISHED", "tick", tick));
  }
}
