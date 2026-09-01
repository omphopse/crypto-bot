package io.algopilot.agent.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StructuredTradeDecision(
    UUID id,
    UUID contextId,
    String contextHash,
    UUID botId,
    UUID sessionId,
    UUID strategyVersionId,
    String provider,
    String model,
    TradeAction decision,
    String symbol,
    String side,
    BigDecimal confidence,
    BigDecimal quantity,
    BigDecimal referencePrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    String timeHorizon,
    String thesis,
    List<UUID> evidenceReferences,
    List<String> riskFactors,
    List<String> invalidationConditions,
    ValidationStatus validationStatus,
    String rejectionReason,
    long latencyMs,
    int inputTokens,
    int outputTokens,
    BigDecimal estimatedCostUsd,
    Instant decisionTimestamp,
    Instant expiresAt
) {
  public boolean isFresh(Instant now) {
    return now.isBefore(expiresAt);
  }

  public boolean isActionable() {
    return validationStatus == ValidationStatus.VALIDATED &&
        (decision == TradeAction.BUY || decision == TradeAction.SELL || decision == TradeAction.CLOSE || decision == TradeAction.REDUCE);
  }
}
