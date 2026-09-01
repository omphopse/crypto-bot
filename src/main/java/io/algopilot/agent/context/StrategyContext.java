package io.algopilot.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record StrategyContext(
    UUID strategyId,
    UUID strategyVersionId,
    String name,
    int versionNumber,
    String symbol,
    String timeframe,
    JsonNode parameters,
    String status
) {}
