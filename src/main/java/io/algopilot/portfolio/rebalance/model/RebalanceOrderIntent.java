package io.algopilot.portfolio.rebalance.model;

import java.math.BigDecimal;

public record RebalanceOrderIntent(
    String symbol,
    String side,
    BigDecimal quantity,
    BigDecimal estimatedPrice,
    BigDecimal deltaCapital,
    String reason
) {}
