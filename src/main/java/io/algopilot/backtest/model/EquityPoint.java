package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(
    Instant timestamp,
    BigDecimal equity,
    BigDecimal drawdownPct
) {}
