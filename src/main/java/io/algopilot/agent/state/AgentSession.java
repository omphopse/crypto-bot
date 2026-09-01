package io.algopilot.agent.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted state of an active or historical autonomous trading agent session.
 */
public record AgentSession(
    UUID id,
    UUID botId,
    String name,
    AgentState currentState,
    AutonomousMode mode,
    JsonNode configuration,
    Instant startedAt,
    Instant lastHeartbeatAt,
    Instant stoppedAt
) {}
