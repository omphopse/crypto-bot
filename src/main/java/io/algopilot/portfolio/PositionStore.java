package io.algopilot.portfolio;

import java.util.List;
import java.util.Optional;

public interface PositionStore {
  Optional<Position> find(String botId, String symbol);
  List<Position> findByBotId(String botId);
  Position save(Position position);
}
