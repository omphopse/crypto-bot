package io.algopilot.reconciliation.broker;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerAccountBalance(
    String currency,
    BigDecimal cash,
    BigDecimal buyingPower,
    BigDecimal equity,
    Instant timestamp
) {}
