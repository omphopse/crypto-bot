package io.algopilot.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Position(UUID id, String botId, String symbol, BigDecimal quantity, BigDecimal averageEntryPrice, BigDecimal realizedPnl, Instant updatedAt) {}
