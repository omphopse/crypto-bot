package io.algopilot.agent.context;

import java.math.BigDecimal;

public record RiskContext(
    String riskState,
    BigDecimal dailyLoss,
    BigDecimal currentDrawdown,
    BigDecimal maxDrawdown,
    BigDecimal singleSymbolExposure,
    BigDecimal maxSingleSymbolExposure,
    BigDecimal portfolioExposure,
    BigDecimal maxPortfolioExposure,
    boolean isEmergencyStopped,
    boolean isReconciliationBlocked,
    boolean isTradingBlocked
) {}
