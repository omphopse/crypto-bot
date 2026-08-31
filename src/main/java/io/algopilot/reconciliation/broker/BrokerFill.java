package io.algopilot.reconciliation.broker;

import io.algopilot.risk.RiskDecisionRequest.Side;
import java.math.BigDecimal;
import java.time.Instant;

public record BrokerFill(
    String exchangeFillId,
    String brokerOrderId,
    String clientOrderId,
    String botId,
    String symbol,
    Side side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fee,
    Instant filledAt
) {}
