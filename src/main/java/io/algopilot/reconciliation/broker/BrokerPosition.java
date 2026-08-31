package io.algopilot.reconciliation.broker;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerPosition(
    String botId,
    String symbol,
    BigDecimal quantity,
    BigDecimal averageEntryPrice,
    BigDecimal unrealizedPnl,
    BigDecimal marketValue,
    Instant updatedAt
) {}
