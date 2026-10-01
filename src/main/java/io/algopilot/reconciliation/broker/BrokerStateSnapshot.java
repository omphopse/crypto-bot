package io.algopilot.reconciliation.broker;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import java.time.Instant;
import java.util.List;

public record BrokerStateSnapshot(
    Broker broker,
    ExecutionMode mode,
    String botId,
    BrokerAccountBalance balance,
    List<BrokerOrder> openOrders,
    List<BrokerFill> fills,
    List<BrokerPosition> positions,
    Instant snapshotTime,
    String brokerAccountId
) {
  public BrokerStateSnapshot(Broker broker, ExecutionMode mode, String botId, BrokerAccountBalance balance,
                             List<BrokerOrder> openOrders, List<BrokerFill> fills, List<BrokerPosition> positions, Instant snapshotTime) {
    this(broker, mode, botId, balance, openOrders, fills, positions, snapshotTime,
         balance != null ? balance.brokerAccountId() : null);
  }
}
