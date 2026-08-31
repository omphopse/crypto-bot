package io.algopilot.portfolio;

import io.algopilot.order.OrderRecord;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PositionAccountingService {
  private final PositionStore store; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public PositionAccountingService(PositionStore store) { this(store, Clock.systemUTC()); }
  PositionAccountingService(PositionStore store, Clock clock) { this.store = store; this.clock = clock; }
  public Position apply(OrderRecord order, BigDecimal fillQuantity, BigDecimal fillPrice, BigDecimal fee) {
    Position old = store.find(order.botId(), order.symbol()).orElse(new Position(UUID.randomUUID(), order.botId(), order.symbol(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, clock.instant()));
    BigDecimal delta = order.side() == RiskDecisionRequest.Side.BUY ? fillQuantity : fillQuantity.negate();
    BigDecimal nextQuantity = old.quantity().add(delta); BigDecimal realized = old.realizedPnl(); BigDecimal nextAverage = old.averageEntryPrice();
    if (old.quantity().signum() == 0 || old.quantity().signum() == delta.signum()) {
      nextAverage = old.quantity().abs().multiply(old.averageEntryPrice()).add(fillQuantity.multiply(fillPrice)).divide(nextQuantity.abs(), 12, RoundingMode.HALF_UP);
    } else {
      BigDecimal closed = old.quantity().abs().min(fillQuantity);
      BigDecimal pnl = old.quantity().signum() > 0 ? fillPrice.subtract(old.averageEntryPrice()).multiply(closed) : old.averageEntryPrice().subtract(fillPrice).multiply(closed);
      realized = realized.add(pnl).subtract(fee);
      if (nextQuantity.signum() == 0) nextAverage = BigDecimal.ZERO;
      else if (nextQuantity.signum() != old.quantity().signum()) nextAverage = fillPrice;
    }
    return store.save(new Position(old.id(), old.botId(), old.symbol(), nextQuantity, nextAverage, realized, clock.instant()));
  }
}
