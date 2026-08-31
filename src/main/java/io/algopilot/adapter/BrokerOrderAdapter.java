package io.algopilot.adapter;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderRecord;
import io.algopilot.reconciliation.broker.BrokerOrder;
import java.util.Optional;

public interface BrokerOrderAdapter {
  Broker broker();

  ExecutionMode supportedMode();

  OrderSubmissionResult submitOrder(OrderRecord order);

  OrderCancellationResult cancelOrder(OrderRecord order, String exchangeOrderId);

  Optional<BrokerOrder> getOrderStatus(String clientOrderId, String exchangeOrderId);
}
