package io.algopilot.agent.context;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioContext(
    BigDecimal startingCapital,
    BigDecimal cash,
    BigDecimal currentMarketValue,
    BigDecimal costBasisExposure,
    BigDecimal grossMarketExposure,
    BigDecimal pendingOrderNotional,
    BigDecimal totalReservedExposure,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,
    BigDecimal totalNetPnl,
    BigDecimal fees,
    BigDecimal portfolioEquity,
    BigDecimal riskUtilizationPercent,
    Instant calculatedAt
) {}
