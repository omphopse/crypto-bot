package io.algopilot.bot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotStore {
  Bot save(Bot bot);
  Optional<Bot> findById(UUID id);
  default List<Bot> findAll() { return List.of(); }
  Bot updateStatus(UUID id, BotStatus status);
}
