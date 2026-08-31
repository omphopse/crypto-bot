package io.algopilot.reconciliation.engine;

import io.algopilot.fill.Fill;
import io.algopilot.order.OrderRecord;
import io.algopilot.portfolio.Position;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LocalStateSnapshot(
    String botId,
    BigDecimal cash,
    BigDecimal buyingPower,
    BigDecimal equity,
    List<OrderRecord> openOrders,
    List<Fill> fills,
    List<Position> positions,
    Instant snapshotTime
) {}
