package io.algopilot.agent.state;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentStateStore {
  AgentSession saveSession(AgentSession session);
  Optional<AgentSession> findSessionById(UUID id);
  Optional<AgentSession> findActiveSessionByBotId(UUID botId);
  List<AgentSession> findSessionsByBotId(UUID botId);
  List<AgentSession> findAllSessions();
  AgentStateEvent saveStateEvent(AgentStateEvent event);
  List<AgentStateEvent> findStateEventsBySessionId(UUID sessionId, int limit);
  List<AgentStateEvent> findStateEventsByBotId(UUID botId, int limit);
}
