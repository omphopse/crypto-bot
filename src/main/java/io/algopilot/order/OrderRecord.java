package io.algopilot.order;

import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderRecord(UUID id, String clientOrderId, String botId, String strategyVersionId, String symbol,
                          RiskDecisionRequest.Side side, BigDecimal quantity, BigDecimal referencePrice,
                          OrderStatus status, Instant createdAt) {}
