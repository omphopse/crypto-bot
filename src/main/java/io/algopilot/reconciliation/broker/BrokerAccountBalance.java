package io.algopilot.reconciliation.broker;

import java.math.BigDecimal;
import java.time.Instant;

public record BrokerAccountBalance(
    String currency,
    BigDecimal cash,
    BigDecimal buyingPower,
    BigDecimal equity,
    Instant timestamp,
    String brokerAccountId
) {
  public BrokerAccountBalance(String currency, BigDecimal cash, BigDecimal buyingPower, BigDecimal equity, Instant timestamp) {
    this(currency, cash, buyingPower, equity, timestamp, null);
  }
}
