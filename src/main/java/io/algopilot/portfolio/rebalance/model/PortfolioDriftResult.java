package io.algopilot.portfolio.rebalance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PortfolioDriftResult(
    UUID planId,
    boolean rebalanceRequired,
    BigDecimal maxDriftPct,
    Map<String, BigDecimal> symbolDrifts,
    List<RebalanceOrderIntent> proposedOrders,
    Instant evaluatedAt
) {}
