package io.algopilot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionJournalServiceTest {
  private final MemoryStore store = new MemoryStore();
  private final DecisionJournalService service = new DecisionJournalService(store, mock(AuditEventWriter.class), new ObjectMapper());
  @Test void journals_typed_decision_without_an_execution_dependency() {
    AgentDecision result = service.journal(decision(DecisionAction.BUY, "BTC/USD", BigDecimal.ONE));
    assertThat(result.action()).isEqualTo(DecisionAction.BUY); assertThat(store.saved).isEqualTo(result);
  }
  @Test void rejects_buy_without_quantity_before_persisting() {
    assertThatThrownBy(() -> service.journal(decision(DecisionAction.BUY, "BTC/USD", null))).isInstanceOf(DecisionValidationException.class).hasMessage("QUANTITY_REQUIRED_FOR_ACTION");
    assertThat(store.saved).isNull();
  }
  private StructuredDecisionRequest decision(DecisionAction action, String symbol, BigDecimal quantity) { return new StructuredDecisionRequest(UUID.randomUUID(), UUID.randomUUID(), action, symbol, new BigDecimal("0.72"), quantity, "Momentum remains intact.", List.of(new DecisionEvidence("market-1", EvidenceKind.FACT, "Price crossed EMA.")), List.of("Volatility"), List.of("Break below EMA")); }
  private static final class MemoryStore implements AgentDecisionStore { AgentDecision saved; public AgentDecision save(AgentDecision value) { return saved = value; } }
}
