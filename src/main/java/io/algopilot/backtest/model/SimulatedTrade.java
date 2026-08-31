package io.algopilot.backtest.model;

import io.algopilot.risk.RiskDecisionRequest.Side;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SimulatedTrade(
    UUID id,
    UUID backtestId,
    String symbol,
    Side side,
    Instant entryTime,
    Instant exitTime,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal pnl,
    BigDecimal fee,
    BigDecimal returnPct,
    String exitReason
) {}
