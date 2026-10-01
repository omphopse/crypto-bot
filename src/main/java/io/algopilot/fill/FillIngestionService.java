package io.algopilot.fill;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.order.OrderTransitionRequest;
import io.algopilot.portfolio.PositionAccountingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FillIngestionService {
  private final FillStore fills; private final OrderStore orders; private final OrderLifecycleService lifecycle; private final PositionAccountingService positions; private final AuditEventWriter audit; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public FillIngestionService(FillStore fills, OrderStore orders, OrderLifecycleService lifecycle, PositionAccountingService positions, AuditEventWriter audit) { this(fills, orders, lifecycle, positions, audit, Clock.systemUTC()); }
  FillIngestionService(FillStore fills, OrderStore orders, OrderLifecycleService lifecycle, PositionAccountingService positions, AuditEventWriter audit, Clock clock) { this.fills = fills; this.orders = orders; this.lifecycle = lifecycle; this.positions = positions; this.audit = audit; this.clock = clock; }
  @Transactional public Fill ingest(FillReport report) {
    var duplicate = fills.findByExchangeFillId(report.exchangeFillId()); if (duplicate.isPresent()) return duplicate.get();
    OrderRecord order = orders.findById(report.orderId()).orElseThrow(() -> new FillRejectedException("ORDER_NOT_FOUND"));
    if (!(order.status() == OrderStatus.SUBMITTED || order.status() == OrderStatus.ACKNOWLEDGED || order.status() == OrderStatus.PARTIALLY_FILLED)) throw new FillRejectedException("ORDER_NOT_FILLABLE");
    BigDecimal total = fills.totalQuantityForOrder(order.id()).add(report.quantity()); if (total.compareTo(order.quantity()) > 0) throw new FillRejectedException("FILL_EXCEEDS_ORDER_QUANTITY");
    Fill fill = fills.save(new Fill(UUID.randomUUID(), order.id(), report.exchangeFillId(), report.quantity(), report.price(), report.fee(), clock.instant(), report.brokerAccountId()));
    positions.apply(order, fill.quantity(), fill.price(), fill.fee());
    lifecycle.transition(order.id(), new OrderTransitionRequest(total.compareTo(order.quantity()) == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, null, "exchange fill " + fill.exchangeFillId()));
    audit.record("EXECUTION", order.botId(), "FILL_RECORDED", "ORDER", order.id().toString(), Map.of("exchangeFillId", fill.exchangeFillId(), "quantity", fill.quantity(), "price", fill.price()));
    return fill;
  }
}
