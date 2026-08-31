package io.algopilot.reconciliation.broker;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import java.time.Instant;
import java.util.List;

/**
 * Modular broker-state abstraction for fetching external balance, open orders, fills, and positions.
 * Production implementations must not contain live exchange assumptions and must operate strictly
 * in supported modes (e.g. ALPACA_PAPER, BYBIT_DEMO).
 */
public interface BrokerStateProvider {
  BrokerAccountBalance fetchBalance(Broker broker, ExecutionMode mode);

  List<BrokerOrder> fetchOpenOrders(Broker broker, ExecutionMode mode, String botId);

  List<BrokerFill> fetchFills(Broker broker, ExecutionMode mode, String botId, Instant since);

  List<BrokerPosition> fetchPositions(Broker broker, ExecutionMode mode, String botId);

  default BrokerStateSnapshot fetchSnapshot(Broker broker, ExecutionMode mode, String botId) {
    return new BrokerStateSnapshot(
        broker,
        mode,
        botId,
        fetchBalance(broker, mode),
        fetchOpenOrders(broker, mode, botId),
        fetchFills(broker, mode, botId, Instant.EPOCH),
        fetchPositions(broker, mode, botId),
        Instant.now()
    );
  }
}
