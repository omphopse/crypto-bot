package io.algopilot.portfolio;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
  private final PositionStore store;

  public PositionController(PositionStore store) {
    this.store = store;
  }

  @GetMapping
  public List<Position> list(@RequestParam(required = false) String botId) {
    if (botId != null && !botId.isBlank()) {
      return store.findByBotId(botId);
    }
    return store.findAll();
  }
}
