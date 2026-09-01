package io.algopilot.agent.execution;

import io.algopilot.agent.decision.TradeAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ValidatedTradeIntent(
    UUID intentId,
    UUID decisionId,
    UUID contextId,
    String contextHash,
    UUID botId,
    UUID sessionId,
    UUID strategyId,
    UUID strategyVersionId,
    String symbol,
    TradeAction action,
    String side,
    BigDecimal quantity,
    BigDecimal referencePrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    String timeHorizon,
    Instant createdAt,
    Instant expiresAt
) {
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }
}
