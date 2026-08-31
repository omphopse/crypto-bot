package io.algopilot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Journals structured intent only. It has no dependency on OrderService or any broker adapter. */
@Service
public class DecisionJournalService {
  private final AgentDecisionStore store; private final AuditEventWriter audit; private final ObjectMapper json; private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public DecisionJournalService(AgentDecisionStore store, AuditEventWriter audit, ObjectMapper json) { this(store, audit, json, Clock.systemUTC()); }
  DecisionJournalService(AgentDecisionStore store, AuditEventWriter audit, ObjectMapper json, Clock clock) { this.store = store; this.audit = audit; this.json = json; this.clock = clock; }
  @Transactional public AgentDecision journal(StructuredDecisionRequest request) {
    validate(request);
    try {
      AgentDecision decision = store.save(new AgentDecision(UUID.randomUUID(), request.botId(), request.strategyVersionId(), request.action(), request.symbol(), json.writeValueAsString(request), clock.instant()));
      audit.record("AGENT", request.botId().toString(), "AGENT_DECISION_JOURNALED", "AGENT_DECISION", decision.id().toString(), Map.of("action", request.action().name(), "strategyVersionId", request.strategyVersionId().toString()));
      return decision;
    } catch (JsonProcessingException error) { throw new IllegalArgumentException("Decision cannot be serialized", error); }
  }
  private void validate(StructuredDecisionRequest request) {
    boolean executionIntent = request.action() != DecisionAction.HOLD;
    if (executionIntent && (request.symbol() == null || request.symbol().isBlank())) throw new DecisionValidationException("SYMBOL_REQUIRED_FOR_ACTION");
    if ((request.action() == DecisionAction.BUY || request.action() == DecisionAction.SELL || request.action() == DecisionAction.REDUCE) && request.quantity() == null) throw new DecisionValidationException("QUANTITY_REQUIRED_FOR_ACTION");
  }
}
