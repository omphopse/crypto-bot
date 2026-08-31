package io.algopilot.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTick(
    String symbol,
    BigDecimal price,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal volume,
    Instant timestamp
) {}
