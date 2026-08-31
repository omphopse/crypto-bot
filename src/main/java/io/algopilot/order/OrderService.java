package io.algopilot.order;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.risk.RiskDecision;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskDecisionService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final RiskDecisionService risk; private final OrderStore orders; private final AuditEventWriter audit; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public OrderService(RiskDecisionService risk, OrderStore orders, AuditEventWriter audit) { this(risk, orders, audit, Clock.systemUTC()); }
  public OrderService(RiskDecisionService risk, OrderStore orders, AuditEventWriter audit, Clock clock) { this.risk = risk; this.orders = orders; this.audit = audit; this.clock = clock; }
  @Transactional(noRollbackFor = OrderRejectedException.class)
  public OrderRecord create(RiskDecisionRequest command) {
    var existing = orders.findByClientOrderId(command.clientOrderId());
    if (existing.isPresent()) return existing.get(); // idempotent retry: do not evaluate or create a second order
    RiskDecision decision = risk.evaluate(command);
    if (decision.status() == RiskDecision.Status.REJECTED) throw new OrderRejectedException(decision);
    OrderRecord created = new OrderRecord(UUID.randomUUID(), command.clientOrderId(), command.botId(), command.strategyVersionId(), command.symbol(), command.side(), command.quantity(), command.referencePrice(), OrderStatus.CREATED, clock.instant());
    orders.save(created);
    audit.record("SYSTEM", command.botId(), "ORDER_CREATED", "ORDER", created.id().toString(), Map.of("clientOrderId", created.clientOrderId(), "status", created.status().name()));
    return created;
  }
}
