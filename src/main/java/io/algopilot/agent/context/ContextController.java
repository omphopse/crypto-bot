package io.algopilot.agent.context;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/context")
public class ContextController {
  private final ContextBuilderService contextBuilder;
  private final TradingContextStore store;

  public ContextController(ContextBuilderService contextBuilder, TradingContextStore store) {
    this.contextBuilder = contextBuilder;
    this.store = store;
  }

  @GetMapping("/{botId}")
  public ResponseEntity<TradingContext> buildLiveContext(@PathVariable UUID botId) {
    return ResponseEntity.ok(contextBuilder.buildContext(botId));
  }

  @GetMapping("/{botId}/latest")
  public ResponseEntity<TradingContext> getLatestContext(@PathVariable UUID botId) {
    return store.findLatestByBotId(botId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.ok(contextBuilder.buildContext(botId)));
  }

  @GetMapping("/{botId}/history")
  public ResponseEntity<List<TradingContext>> getContextHistory(
      @PathVariable UUID botId,
      @RequestParam(defaultValue = "10") int limit) {
    return ResponseEntity.ok(store.findRecentByBotId(botId, limit));
  }
}
