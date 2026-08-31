package io.algopilot.strategy;

import java.time.Instant;
import java.util.UUID;

public record Strategy(UUID id, String name, String status, Instant createdAt) {}
