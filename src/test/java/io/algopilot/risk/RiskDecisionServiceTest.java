package io.algopilot.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RiskDecisionServiceTest {
  @Test void persists_the_typed_snapshot_and_rejection_reasons() {
    MemoryStore store = new MemoryStore(); RiskDecisionService service = new RiskDecisionService(new RiskEngine(), store, mock(AuditEventWriter.class), new ObjectMapper().findAndRegisterModules());
    RiskDecision decision = service.evaluate(new RiskDecisionRequest("risk-1", "bot", "v1", "BTC/USD", RiskDecisionRequest.Side.BUY, BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), false, true, false, 0, 0, 0));
    assertThat(decision.status()).isEqualTo(RiskDecision.Status.REJECTED); assertThat(store.record.requestSnapshot()).contains("risk-1"); assertThat(store.record.decision().reasons()).contains(RiskDecision.Reason.EMERGENCY_STOP);
  }
  private static final class MemoryStore implements RiskDecisionStore { PersistedRiskDecision record; public PersistedRiskDecision save(PersistedRiskDecision value) { return record = value; } }
}
