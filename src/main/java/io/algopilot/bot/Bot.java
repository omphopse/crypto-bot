package io.algopilot.bot;

import java.time.Instant;
import java.util.UUID;

public record Bot(UUID id, String name, UUID strategyVersionId, Broker broker, ExecutionMode executionMode, BotStatus status, Instant createdAt) {}
