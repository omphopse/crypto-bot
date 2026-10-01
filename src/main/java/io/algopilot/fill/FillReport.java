package io.algopilot.fill;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Normalized report received from a future broker adapter, never from the agent or browser. */
public record FillReport(
    @NotNull UUID orderId,
    @NotBlank String exchangeFillId,
    @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
    @NotNull @DecimalMin(value = "0.00000001") BigDecimal price,
    @NotNull @DecimalMin(value = "0") BigDecimal fee,
    String brokerAccountId
) {
  public FillReport(UUID orderId, String exchangeFillId, BigDecimal quantity, BigDecimal price, BigDecimal fee) {
    this(orderId, exchangeFillId, quantity, price, fee, null);
  }
}
