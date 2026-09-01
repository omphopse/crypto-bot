package io.algopilot.agent.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of an agent state machine transition.
 */
public record AgentStateEvent(
    UUID id,
    UUID sessionId,
    UUID botId,
    AgentState fromState,
    AgentState toState,
    String reason,
    JsonNode metadata,
    Instant timestamp
) {}
