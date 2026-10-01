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
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
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
  private final ReconciliationStore reconciliationStore;
  private final BrokerStateProvider brokerStateProvider;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit,
      @org.springframework.lang.Nullable ReconciliationStore reconciliationStore,
      @org.springframework.beans.factory.annotation.Autowired(required = false) @org.springframework.lang.Nullable BrokerStateProvider brokerStateProvider,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this.adapters = adapters;
    this.botStore = botStore;
    this.orderStore = orderStore;
    this.lifecycleService = lifecycleService;
    this.audit = audit;
    this.reconciliationStore = reconciliationStore;
    this.brokerStateProvider = brokerStateProvider;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit,
      @org.springframework.lang.Nullable ReconciliationStore reconciliationStore,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this(adapters, botStore, orderStore, lifecycleService, audit, reconciliationStore, null, clock);
  }

  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit,
      Clock clock) {
    this(adapters, botStore, orderStore, lifecycleService, audit, null, null, clock);
  }

  public ExecutionGateway(
      List<BrokerOrderAdapter> adapters,
      BotStore botStore,
      OrderStore orderStore,
      OrderLifecycleService lifecycleService,
      AuditEventWriter audit) {
    this(adapters, botStore, orderStore, lifecycleService, audit, null, null, Clock.systemUTC());
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
      if (bot.status() == BotStatus.PAUSED && order.side() == RiskDecisionRequest.Side.SELL) {
        BigDecimal brokerHeld = getBrokerHeldQuantity(bot, order.symbol());
        if (brokerHeld.compareTo(BigDecimal.ZERO) <= 0) {
          throw new BrokerAdapterException("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER: PAUSED and zero broker position to sell.");
        }
        if (order.quantity().compareTo(brokerHeld) > 0) {
          throw new BrokerAdapterException("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER: Sell quantity " + order.quantity() + " exceeds broker held quantity " + brokerHeld);
        }
        log.warn("PERMITTING_PAUSED_EXPOSURE_REDUCING_SELL: orderId={} qty={} brokerHeld={}", order.id(), order.quantity(), brokerHeld);
      } else {
        throw new BrokerAdapterException("BOT_NOT_RUNNING_CANNOT_DISPATCH_ORDER:" + bot.status());
      }
    }

    if (reconciliationStore != null) {
      var mismatches = reconciliationStore.findMismatchesByBotId(bot.id().toString(), ResolutionState.UNRESOLVED);
      boolean hasCritical = mismatches.stream().anyMatch(m -> m.severity() == MismatchSeverity.CRITICAL);
      if (hasCritical) {
        if (order.side() != RiskDecisionRequest.Side.SELL) {
          throw new BrokerAdapterException("BOT_RECONCILIATION_MISMATCH_BLOCK: Bot has " + mismatches.size() + " unresolved critical reconciliation mismatches.");
        }
        BigDecimal brokerHeld = getBrokerHeldQuantity(bot, order.symbol());
        if (brokerHeld.compareTo(BigDecimal.ZERO) <= 0) {
          throw new BrokerAdapterException("BOT_RECONCILIATION_MISMATCH_BLOCK: Bot has unresolved critical reconciliation mismatches and no broker position to sell.");
        }
        if (order.quantity().compareTo(brokerHeld) > 0) {
          throw new BrokerAdapterException("BOT_RECONCILIATION_MISMATCH_BLOCK: Sell quantity " + order.quantity() + " exceeds broker held quantity " + brokerHeld);
        }
        log.warn("PERMITTING_EXPOSURE_REDUCING_SELL during critical reconciliation mismatch: orderId={} qty={} brokerHeld={}", order.id(), order.quantity(), brokerHeld);
      }
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

  private BigDecimal getBrokerHeldQuantity(Bot bot, String symbol) {
    if (brokerStateProvider == null) {
      log.warn("BrokerStateProvider not configured; cannot verify broker held position safely");
      throw new BrokerAdapterException("BROKER_STATE_UNAVAILABLE: Cannot verify broker position");
    }
    try {
      List<BrokerPosition> positions = brokerStateProvider.fetchPositions(bot.broker(), bot.executionMode(), bot.id().toString());
      if (positions == null || positions.isEmpty()) {
        return BigDecimal.ZERO;
      }
      return positions.stream()
          .filter(p -> symbolMatches(p.symbol(), symbol))
          .map(BrokerPosition::quantity)
          .filter(q -> q != null && q.compareTo(BigDecimal.ZERO) > 0)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    } catch (BrokerAdapterException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to fetch broker positions for bot {}: {}", bot.id(), e.getMessage(), e);
      throw new BrokerAdapterException("BROKER_STATE_FETCH_FAILED: " + e.getMessage());
    }
  }

  private boolean symbolMatches(String s1, String s2) {
    if (s1 == null || s2 == null) return false;
    String norm1 = s1.replace("/", "").replace("-", "").toUpperCase();
    String norm2 = s2.replace("/", "").replace("-", "").toUpperCase();
    return norm1.equals(norm2);
  }
}
