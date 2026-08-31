package io.algopilot.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionAccountingServiceTest {
  @Test void maintains_average_entry_then_realizes_pnl_when_reducing() {
    MemoryStore store = new MemoryStore(); PositionAccountingService service = new PositionAccountingService(store);
    service.apply(order(RiskDecisionRequest.Side.BUY), new BigDecimal("2"), new BigDecimal("100"), BigDecimal.ZERO);
    Position reduced = service.apply(order(RiskDecisionRequest.Side.SELL), BigDecimal.ONE, new BigDecimal("120"), new BigDecimal("1"));
    assertThat(reduced.quantity()).isEqualByComparingTo("1"); assertThat(reduced.averageEntryPrice()).isEqualByComparingTo("100"); assertThat(reduced.realizedPnl()).isEqualByComparingTo("19");
  }
  private OrderRecord order(RiskDecisionRequest.Side side) { return new OrderRecord(UUID.randomUUID(), UUID.randomUUID().toString(), "bot", "v1", "BTC/USD", side, new BigDecimal("2"), new BigDecimal("100"), OrderStatus.PARTIALLY_FILLED, Instant.now()); }
  private static final class MemoryStore implements PositionStore {
    final HashMap<String, Position> data = new HashMap<>();
    public Optional<Position> find(String bot, String symbol) { return Optional.ofNullable(data.get(bot + symbol)); }
    public List<Position> findByBotId(String botId) { return data.values().stream().filter(p -> p.botId().equals(botId)).toList(); }
    public Position save(Position position) { data.put(position.botId() + position.symbol(), position); return position; }
  }
}
