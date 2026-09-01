package io.algopilot.reset.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PreflightStatusResponse(
    boolean localDatabaseClean,
    boolean redisClean,
    int activeBots,
    int localOrders,
    int localFills,
    int localPositions,
    int localTrades,
    int agentSessions,
    int agentDecisions,
    int researchRecords,
    int strategyCandidates,
    int canaryRuns,
    BigDecimal brokerBalance,
    int brokerOpenOrders,
    int brokerOpenPositions,
    String brokerStatus,
    String systemMode,
    boolean liveTradingDisabled,
    Instant timestamp
) {}
