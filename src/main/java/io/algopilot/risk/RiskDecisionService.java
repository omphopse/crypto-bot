package io.algopilot.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only application service that evaluates and records an order risk decision. */
@Service
public class RiskDecisionService {
  private final RiskEngine engine; private final RiskDecisionStore store; private final AuditEventWriter audit; private final ObjectMapper json; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public RiskDecisionService(RiskEngine engine, RiskDecisionStore store, AuditEventWriter audit, ObjectMapper json) { this(engine, store, audit, json, Clock.systemUTC()); }
  RiskDecisionService(RiskEngine engine, RiskDecisionStore store, AuditEventWriter audit, ObjectMapper json, Clock clock) { this.engine = engine; this.store = store; this.audit = audit; this.json = json; this.clock = clock; }
  @Transactional public RiskDecision evaluate(RiskDecisionRequest request) {
    RiskDecision decision = engine.evaluate(request);
    try {
      store.save(new PersistedRiskDecision(UUID.randomUUID(), request.clientOrderId(), request.botId(), request.strategyVersionId(), decision, json.writeValueAsString(request), clock.instant()));
      audit.record("SYSTEM", request.botId(), "RISK_EVALUATED", "ORDER", request.clientOrderId(), Map.of("status", decision.status().name(), "reasons", decision.reasons()));
      return decision;
    } catch (JsonProcessingException error) { throw new IllegalArgumentException("Risk request cannot be serialized", error); }
  }
}
