package io.algopilot.adapter;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotNotFoundException;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderNotFoundException;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.order.OrderTransitionRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-bypassable Execution Gateway.
 * Routes risk-approved OrderRecords to external Paper/Demo broker adapters.
 * Guarantees bot state verification, live-trading blocking, order lifecycle transitions, and audit trail writing.
 */
@Service
public class ExecutionGateway {
  private static final Logger log = LoggerFactory.getLogger(ExecutionGateway.class);

  private final List<BrokerOrderAdapter> adapters;
  private final BotStore botStore;
  private final OrderStore orderStore;
  private final OrderLifecycleService lifecycleService;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit) {
    this(adapters, botStore, orderStore, lifecycleService, audit, Clock.systemUTC());
  }

  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit,
      Clock clock) {
    this.adapters = adapters;
    this.botStore = botStore;
    this.orderStore = orderStore;
    this.lifecycleService = lifecycleService;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public OrderSubmissionResult dispatch(UUID orderId) {
    OrderRecord order = orderStore.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

    UUID botUuid;
    try {
      botUuid = UUID.fromString(order.botId());
    } catch (IllegalArgumentException e) {
      throw new BrokerAdapterException("INVALID_BOT_ID:" + order.botId());
    }

    Bot bot = botStore.findById(botUuid).orElseThrow(() -> new BotNotFoundException(botUuid));

    if (bot.executionMode() == ExecutionMode.LIVE) {
      throw new BrokerAdapterException("LIVE_TRADING_DISABLED");
    }

    if (bot.status() != BotStatus.RUNNING) {
      throw new BrokerAdapterException("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER:" + bot.status());
    }

    if (order.status() != OrderStatus.CREATED) {
      throw new BrokerAdapterException("ORDER_NOT_IN_CREATION_STATE:" + order.status());
    }

    BrokerOrderAdapter adapter = resolveAdapter(bot.broker(), bot.executionMode());

    log.info("DISPATCHING_ORDER clientOrderId={} to broker={} mode={}", order.clientOrderId(), bot.broker(), bot.executionMode());
    OrderSubmissionResult result = adapter.submitOrder(order);

    lifecycleService.transition(order.id(), new OrderTransitionRequest(
        result.status(),
        result.exchangeOrderId(),
        "Dispatched to " + bot.broker() + " - exchangeOrderId=" + result.exchangeOrderId()
    ));

    audit.record(
        "EXECUTION",
        bot.id().toString(),
        "ORDER_DISPATCHED_TO_BROKER",
        "ORDER",
        order.id().toString(),
        Map.of(
            "clientOrderId", order.clientOrderId(),
            "exchangeOrderId", result.exchangeOrderId() != null ? result.exchangeOrderId() : "",
            "broker", bot.broker().name(),
            "mode", bot.executionMode().name(),
            "status", result.status().name()
        )
    );

    return result;
  }

  @Transactional
  public OrderCancellationResult cancel(UUID orderId, String exchangeOrderId) {
    OrderRecord order = orderStore.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    UUID botUuid = UUID.fromString(order.botId());
    Bot bot = botStore.findById(botUuid).orElseThrow(() -> new BotNotFoundException(botUuid));

    BrokerOrderAdapter adapter = resolveAdapter(bot.broker(), bot.executionMode());
    OrderCancellationResult result = adapter.cancelOrder(order, exchangeOrderId);

    lifecycleService.transition(order.id(), new OrderTransitionRequest(
        OrderStatus.CANCEL_REQUESTED,
        exchangeOrderId,
        "Order cancellation requested via " + bot.broker()
    ));

    audit.record(
        "EXECUTION",
        bot.id().toString(),
        "ORDER_CANCELLATION_REQUESTED",
        "ORDER",
        order.id().toString(),
        Map.of(
            "clientOrderId", order.clientOrderId(),
            "exchangeOrderId", exchangeOrderId != null ? exchangeOrderId : "",
            "broker", bot.broker().name()
        )
    );

    return result;
  }

  private BrokerOrderAdapter resolveAdapter(io.algopilot.bot.Broker broker, ExecutionMode mode) {
    for (BrokerOrderAdapter adapter : adapters) {
      if (adapter.broker() == broker && adapter.supportedMode() == mode) {
        return adapter;
      }
    }
    throw new BrokerAdapterException("NO_ADAPTER_FOR_BROKER:" + broker + "_" + mode);
  }
}
