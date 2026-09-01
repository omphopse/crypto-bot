package io.algopilot.portfolio.accounting;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Authoritative mark-to-market portfolio financial summary.
 * Strictly guarantees the invariant:
 * Portfolio Equity = Cash + Current Market Value == Starting Capital + Total Net P&L.
 */
public record PortfolioSummary(
    BigDecimal startingCapital,
    BigDecimal cash,
    BigDecimal costBasisExposure,
    BigDecimal marketExposure,
    BigDecimal grossExposure,
    BigDecimal unrealizedPnl,
    BigDecimal realizedPnl,
    BigDecimal cumulativeFees,
    BigDecimal netRealizedPnl,
    BigDecimal totalNetPnl,
    BigDecimal portfolioEquity,
    BigDecimal pendingOrderNotional,
    BigDecimal totalReservedExposure,
    BigDecimal riskUtilizationPercent,
    Instant updatedAt
) {}
