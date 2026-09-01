package io.algopilot.research.model;

import java.time.Instant;
import java.util.UUID;

public record ResearchRequest(
    UUID id,
    UUID sessionId,
    UUID botId,
    String asset,
    String topic,
    String query,
    int maxResults,
    int freshnessMinutes,
    String status,
    Instant requestedAt,
    Instant completedAt
) {}
