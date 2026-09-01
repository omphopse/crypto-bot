package io.algopilot.agent.context;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradingContextStore {
  TradingContext save(TradingContext context);
  Optional<TradingContext> findLatestByBotId(UUID botId);
  List<TradingContext> findRecentByBotId(UUID botId, int limit);
  Optional<TradingContext> findById(UUID id);
}
