package io.algopilot.reconciliation.engine;

import io.algopilot.fill.Fill;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.portfolio.Position;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerFill;
import io.algopilot.reconciliation.broker.BrokerOrder;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ResolutionState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic, side-effect-free reconciliation engine.
 * Compares local state against normalized broker state across:
 * 1. Balances (cash, equity, buying power)
 * 2. Open Orders (presence, quantities, status, prices)
 * 3. Fills (presence, quantities, prices, fees)
 * 4. Positions (quantities, direction/side, average entry prices)
 */
@Service
public class ReconciliationEngine {
  private final Clock clock;

  public ReconciliationEngine() {
    this(Clock.systemUTC());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ReconciliationEngine(@org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public List<ReconciliationMismatch> reconcile(
      UUID runId,
      String botId,
      LocalStateSnapshot localState,
      BrokerStateSnapshot brokerState) {

    List<ReconciliationMismatch> mismatches = new ArrayList<>();
    Instant now = clock.instant();

    reconcileBalances(runId, botId, localState, brokerState.balance(), now, mismatches);
    reconcileOrders(runId, botId, localState.openOrders(), brokerState.openOrders(), now, mismatches);
    reconcileFills(runId, botId, localState.fills(), brokerState.fills(), now, mismatches);
    reconcilePositions(runId, botId, localState.positions(), brokerState.positions(), now, mismatches);

    return List.copyOf(mismatches);
  }

  private void reconcileBalances(
      UUID runId,
      String botId,
      LocalStateSnapshot local,
      BrokerAccountBalance brokerBalance,
      Instant now,
      List<ReconciliationMismatch> mismatches) {

    if (brokerBalance == null) return;

    if (local.cash() != null && brokerBalance.cash() != null && local.cash().compareTo(brokerBalance.cash()) != 0) {
      mismatches.add(new ReconciliationMismatch(
          UUID.randomUUID(), runId, botId,
          MismatchCategory.BALANCE_MISMATCH,
          MismatchType.BALANCE_QUANTITY_MISMATCH,
          MismatchSeverity.CRITICAL,
          brokerBalance.currency() != null ? brokerBalance.currency() : "USD",
          Map.of("field", "cash", "amount", local.cash()),
          Map.of("field", "cash", "amount", brokerBalance.cash()),
          ResolutionState.UNRESOLVED,
          null,
          now
      ));
    }

    if (local.equity() != null && brokerBalance.equity() != null && local.equity().compareTo(brokerBalance.equity()) != 0) {
      mismatches.add(new ReconciliationMismatch(
          UUID.randomUUID(), runId, botId,
          MismatchCategory.BALANCE_MISMATCH,
          MismatchType.BALANCE_QUANTITY_MISMATCH,
          MismatchSeverity.CRITICAL,
          brokerBalance.currency() != null ? brokerBalance.currency() : "USD",
          Map.of("field", "equity", "amount", local.equity()),
          Map.of("field", "equity", "amount", brokerBalance.equity()),
          ResolutionState.UNRESOLVED,
          null,
          now
      ));
    }
  }

  private void reconcileOrders(
      UUID runId,
      String botId,
      List<OrderRecord> localOrders,
      List<BrokerOrder> brokerOrders,
      Instant now,
      List<ReconciliationMismatch> mismatches) {

    Map<String, OrderRecord> localByClientOrderId = new HashMap<>();
    if (localOrders != null) {
      for (OrderRecord o : localOrders) {
        if (isOpen(o.status())) {
          localByClientOrderId.put(o.clientOrderId(), o);
        }
      }
    }

    Map<String, BrokerOrder> brokerByClientOrderId = new HashMap<>();
    if (brokerOrders != null) {
      for (BrokerOrder bo : brokerOrders) {
        if (bo.clientOrderId() != null && !bo.clientOrderId().isBlank()) {
          brokerByClientOrderId.put(bo.clientOrderId(), bo);
        }
      }
    }

    // 1. Check local open orders against broker
    for (Map.Entry<String, OrderRecord> entry : localByClientOrderId.entrySet()) {
      String clientOrderId = entry.getKey();
      OrderRecord localOrder = entry.getValue();
      BrokerOrder brokerOrder = brokerByClientOrderId.get(clientOrderId);

      if (brokerOrder == null) {
        mismatches.add(new ReconciliationMismatch(
            UUID.randomUUID(), runId, botId,
            MismatchCategory.ORDER_MISMATCH,
            MismatchType.LOCAL_ORDER_MISSING_BROKER_ORDER,
            MismatchSeverity.CRITICAL,
            localOrder.symbol(),
            Map.of("clientOrderId", clientOrderId, "status", localOrder.status().name(), "quantity", localOrder.quantity()),
            Map.of("clientOrderId", clientOrderId, "status", "MISSING_ON_BROKER"),
            ResolutionState.UNRESOLVED,
            null,
            now
        ));
      } else {
        // Compare quantity
        if (localOrder.quantity().compareTo(brokerOrder.quantity()) != 0) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.ORDER_MISMATCH,
              MismatchType.ORDER_QUANTITY_MISMATCH,
              MismatchSeverity.CRITICAL,
              localOrder.symbol(),
              Map.of("clientOrderId", clientOrderId, "quantity", localOrder.quantity()),
              Map.of("clientOrderId", clientOrderId, "quantity", brokerOrder.quantity()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }

        // Compare status
        if (!isStatusEquivalent(localOrder.status(), brokerOrder.status())) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.ORDER_MISMATCH,
              MismatchType.ORDER_STATUS_MISMATCH,
              MismatchSeverity.CRITICAL,
              localOrder.symbol(),
              Map.of("clientOrderId", clientOrderId, "status", localOrder.status().name()),
              Map.of("clientOrderId", clientOrderId, "status", brokerOrder.status().name()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }

        // Compare price
        if (localOrder.referencePrice() != null && brokerOrder.price() != null
            && localOrder.referencePrice().compareTo(brokerOrder.price()) != 0) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.ORDER_MISMATCH,
              MismatchType.ORDER_PRICE_MISMATCH,
              MismatchSeverity.WARNING,
              localOrder.symbol(),
              Map.of("clientOrderId", clientOrderId, "price", localOrder.referencePrice()),
              Map.of("clientOrderId", clientOrderId, "price", brokerOrder.price()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }
      }
    }

    // 2. Check broker open orders missing locally
    for (Map.Entry<String, BrokerOrder> entry : brokerByClientOrderId.entrySet()) {
      String clientOrderId = entry.getKey();
      BrokerOrder brokerOrder = entry.getValue();
      if (!localByClientOrderId.containsKey(clientOrderId)) {
        mismatches.add(new ReconciliationMismatch(
            UUID.randomUUID(), runId, botId,
            MismatchCategory.ORDER_MISMATCH,
            MismatchType.BROKER_ORDER_MISSING_LOCAL_ORDER,
            MismatchSeverity.CRITICAL,
            brokerOrder.symbol(),
            Map.of("clientOrderId", clientOrderId, "status", "MISSING_LOCALLY"),
            Map.of("clientOrderId", clientOrderId, "brokerOrderId", brokerOrder.brokerOrderId() == null ? "" : brokerOrder.brokerOrderId(), "status", brokerOrder.status().name(), "quantity", brokerOrder.quantity()),
            ResolutionState.UNRESOLVED,
            null,
            now
        ));
      }
    }
  }

  private void reconcileFills(
      UUID runId,
      String botId,
      List<Fill> localFills,
      List<BrokerFill> brokerFills,
      Instant now,
      List<ReconciliationMismatch> mismatches) {

    Map<String, Fill> localByExchangeFillId = new HashMap<>();
    if (localFills != null) {
      for (Fill f : localFills) {
        localByExchangeFillId.put(f.exchangeFillId(), f);
      }
    }

    Map<String, BrokerFill> brokerByExchangeFillId = new HashMap<>();
    if (brokerFills != null) {
      for (BrokerFill bf : brokerFills) {
        brokerByExchangeFillId.put(bf.exchangeFillId(), bf);
      }
    }

    // 1. Check local fills against broker fills
    for (Map.Entry<String, Fill> entry : localByExchangeFillId.entrySet()) {
      String fillId = entry.getKey();
      Fill localFill = entry.getValue();
      BrokerFill brokerFill = brokerByExchangeFillId.get(fillId);

      if (brokerFill == null) {
        mismatches.add(new ReconciliationMismatch(
            UUID.randomUUID(), runId, botId,
            MismatchCategory.FILL_MISMATCH,
            MismatchType.FILL_MISSING_BROKER,
            MismatchSeverity.CRITICAL,
            "",
            Map.of("exchangeFillId", fillId, "quantity", localFill.quantity(), "price", localFill.price()),
            Map.of("exchangeFillId", fillId, "status", "MISSING_ON_BROKER"),
            ResolutionState.UNRESOLVED,
            null,
            now
        ));
      } else {
        if (localFill.quantity().compareTo(brokerFill.quantity()) != 0) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.FILL_MISMATCH,
              MismatchType.FILL_QUANTITY_MISMATCH,
              MismatchSeverity.CRITICAL,
              brokerFill.symbol() != null ? brokerFill.symbol() : "",
              Map.of("exchangeFillId", fillId, "quantity", localFill.quantity()),
              Map.of("exchangeFillId", fillId, "quantity", brokerFill.quantity()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }

        if (localFill.price().compareTo(brokerFill.price()) != 0) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.FILL_MISMATCH,
              MismatchType.FILL_PRICE_MISMATCH,
              MismatchSeverity.CRITICAL,
              brokerFill.symbol() != null ? brokerFill.symbol() : "",
              Map.of("exchangeFillId", fillId, "price", localFill.price()),
              Map.of("exchangeFillId", fillId, "price", brokerFill.price()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }

        if (localFill.fee() != null && brokerFill.fee() != null && localFill.fee().compareTo(brokerFill.fee()) != 0) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.FILL_MISMATCH,
              MismatchType.FILL_FEE_MISMATCH,
              MismatchSeverity.WARNING,
              brokerFill.symbol() != null ? brokerFill.symbol() : "",
              Map.of("exchangeFillId", fillId, "fee", localFill.fee()),
              Map.of("exchangeFillId", fillId, "fee", brokerFill.fee()),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }
      }
    }

    // 2. Check broker fills missing locally
    for (Map.Entry<String, BrokerFill> entry : brokerByExchangeFillId.entrySet()) {
      String fillId = entry.getKey();
      BrokerFill brokerFill = entry.getValue();
      if (!localByExchangeFillId.containsKey(fillId)) {
        mismatches.add(new ReconciliationMismatch(
            UUID.randomUUID(), runId, botId,
            MismatchCategory.FILL_MISMATCH,
            MismatchType.FILL_MISSING_LOCALLY,
            MismatchSeverity.CRITICAL,
            brokerFill.symbol() != null ? brokerFill.symbol() : "",
            Map.of("exchangeFillId", fillId, "status", "MISSING_LOCALLY"),
            Map.of("exchangeFillId", fillId, "quantity", brokerFill.quantity(), "price", brokerFill.price()),
            ResolutionState.UNRESOLVED,
            null,
            now
        ));
      }
    }
  }

  private void reconcilePositions(
      UUID runId,
      String botId,
      List<Position> localPositions,
      List<BrokerPosition> brokerPositions,
      Instant now,
      List<ReconciliationMismatch> mismatches) {

    Map<String, Position> localBySymbol = new HashMap<>();
    if (localPositions != null) {
      for (Position p : localPositions) {
        localBySymbol.put(p.symbol(), p);
      }
    }

    Map<String, BrokerPosition> brokerBySymbol = new HashMap<>();
    if (brokerPositions != null) {
      for (BrokerPosition bp : brokerPositions) {
        brokerBySymbol.put(bp.symbol(), bp);
      }
    }

    Set<String> allSymbols = new HashSet<>();
    allSymbols.addAll(localBySymbol.keySet());
    allSymbols.addAll(brokerBySymbol.keySet());

    for (String symbol : allSymbols) {
      Position localPos = localBySymbol.get(symbol);
      BrokerPosition brokerPos = brokerBySymbol.get(symbol);

      BigDecimal localQty = localPos != null ? localPos.quantity() : BigDecimal.ZERO;
      BigDecimal brokerQty = brokerPos != null ? brokerPos.quantity() : BigDecimal.ZERO;
      BigDecimal localAvgPrice = localPos != null ? localPos.averageEntryPrice() : BigDecimal.ZERO;
      BigDecimal brokerAvgPrice = brokerPos != null ? brokerPos.averageEntryPrice() : BigDecimal.ZERO;

      // Both zero -> matched
      if (localQty.signum() == 0 && brokerQty.signum() == 0) {
        continue;
      }

      // Quantity mismatch check
      if (localQty.compareTo(brokerQty) != 0) {
        // Direction / Side mismatch
        if (localQty.signum() != 0 && brokerQty.signum() != 0 && localQty.signum() != brokerQty.signum()) {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.POSITION_MISMATCH,
              MismatchType.POSITION_SIDE_MISMATCH,
              MismatchSeverity.CRITICAL,
              symbol,
              Map.of("quantity", localQty, "side", localQty.signum() > 0 ? "LONG" : "SHORT"),
              Map.of("quantity", brokerQty, "side", brokerQty.signum() > 0 ? "LONG" : "SHORT"),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        } else {
          mismatches.add(new ReconciliationMismatch(
              UUID.randomUUID(), runId, botId,
              MismatchCategory.POSITION_MISMATCH,
              MismatchType.POSITION_QUANTITY_MISMATCH,
              MismatchSeverity.CRITICAL,
              symbol,
              Map.of("quantity", localQty),
              Map.of("quantity", brokerQty),
              ResolutionState.UNRESOLVED,
              null,
              now
          ));
        }
      }

      // Average entry price check (when both have non-zero position in the same direction)
      if (localQty.signum() != 0 && brokerQty.signum() != 0 && localQty.signum() == brokerQty.signum()
          && localAvgPrice.compareTo(brokerAvgPrice) != 0) {
        mismatches.add(new ReconciliationMismatch(
            UUID.randomUUID(), runId, botId,
            MismatchCategory.POSITION_MISMATCH,
            MismatchType.POSITION_PRICE_MISMATCH,
            MismatchSeverity.WARNING,
            symbol,
            Map.of("averageEntryPrice", localAvgPrice),
            Map.of("averageEntryPrice", brokerAvgPrice),
            ResolutionState.UNRESOLVED,
            null,
            now
        ));
      }
    }
  }

  private boolean isOpen(OrderStatus status) {
    return status == OrderStatus.CREATED
        || status == OrderStatus.SUBMITTED
        || status == OrderStatus.ACKNOWLEDGED
        || status == OrderStatus.PARTIALLY_FILLED
        || status == OrderStatus.CANCEL_REQUESTED;
  }

  private boolean isStatusEquivalent(OrderStatus local, OrderStatus broker) {
    if (local == broker) return true;
    if ((local == OrderStatus.SUBMITTED || local == OrderStatus.ACKNOWLEDGED)
        && (broker == OrderStatus.SUBMITTED || broker == OrderStatus.ACKNOWLEDGED)) {
      return true;
    }
    return false;
  }
}
