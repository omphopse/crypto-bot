package io.algopilot.bot;

public interface BotStore {
  Bot save(Bot bot);
  java.util.Optional<Bot> findById(java.util.UUID id);
  Bot updateStatus(java.util.UUID id, BotStatus status);
}
