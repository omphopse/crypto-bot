package io.algopilot.risk;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/** Typed boundary between agents/strategies and execution. No free-form command reaches an adapter. */
public record RiskDecisionRequest(
    @NotBlank String clientOrderId,
    @NotBlank String botId,
    @NotBlank String strategyVersionId,
    @NotBlank String symbol,
    @NotNull Side side,
    @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
    @NotNull @DecimalMin(value = "0.00000001") BigDecimal referencePrice,
    @NotNull @DecimalMin(value = "0") BigDecimal accountEquity,
    @NotNull @DecimalMin(value = "0") BigDecimal existingSymbolExposure,
    @NotNull @DecimalMin(value = "0") BigDecimal existingPortfolioExposure,
    @NotNull @DecimalMin(value = "0") BigDecimal realizedDailyLoss,
    @NotNull @DecimalMin(value = "0") BigDecimal drawdownPercent,
    @NotNull @DecimalMin(value = "0") BigDecimal estimatedSpreadPercent,
    @NotNull @DecimalMin(value = "0") BigDecimal estimatedSlippagePercent,
    @NotNull Instant marketDataTimestamp,
    boolean botPaused,
    boolean emergencyStop,
    boolean duplicateOrder,
    int openTrades,
    int tradesToday,
    int consecutiveLosses) {
  public enum Side { BUY, SELL }
}
