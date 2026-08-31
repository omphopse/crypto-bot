package io.algopilot.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BacktestResult(
    UUID id,
    UUID strategyVersionId,
    String symbol,
    String timeframe,
    Instant startTime,
    Instant endTime,
    BigDecimal initialCapital,
    BigDecimal finalEquity,
    BigDecimal totalReturnPct,
    int totalTrades,
    int winningTrades,
    int losingTrades,
    BigDecimal winRate,
    BigDecimal maxDrawdownPct,
    BigDecimal sharpeRatio,
    BigDecimal profitFactor,
    List<EquityPoint> equityCurve,
    List<SimulatedTrade> trades,
    Instant createdAt
) {}
