package io.algopilot.fill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Fill(
    UUID id,
    UUID orderId,
    String exchangeFillId,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fee,
    Instant filledAt,
    String brokerAccountId
) {
  public Fill(UUID id, UUID orderId, String exchangeFillId, BigDecimal quantity, BigDecimal price, BigDecimal fee, Instant filledAt) {
    this(id, orderId, exchangeFillId, quantity, price, fee, filledAt, null);
  }
}
