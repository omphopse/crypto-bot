package io.algopilot.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.decision.ValidationStatus;
import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCostGovernanceServiceTest {
  private MemoryCostStore costStore;
  private AuditEventWriter audit;
  private Clock clock;
  private AiCostGovernanceService service;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    costStore = new MemoryCostStore();
    audit = mock(AuditEventWriter.class);
    service = new AiCostGovernanceService(costStore, audit, clock);

    // Global budget policy: max $10.00 / day
    costStore.saveBudgetPolicy(new AiBudgetPolicy(
        UUID.randomUUID(), BudgetTier.GLOBAL, "GLOBAL", new BigDecimal("10.00"), new BigDecimal("2.00"), 500, 100
    ));
  }

  @Test
  void testEvaluateBudgetStatus_whenUnder70Percent_returnsNormal() {
    costStore.setCostToday(new BigDecimal("5.00"), 50);

    BudgetStatus status = service.evaluateBudgetStatus(null, null);
    assertThat(status).isEqualTo(BudgetStatus.NORMAL);
  }

  @Test
  void testEvaluateBudgetStatus_whenOver70Percent_returnsThrottled() {
    costStore.setCostToday(new BigDecimal("7.50"), 80);

    BudgetStatus status = service.evaluateBudgetStatus(null, null);
    assertThat(status).isEqualTo(BudgetStatus.THROTTLED);
  }

  @Test
  void testEvaluateBudgetStatus_whenExceeds100Percent_returnsBlocked() {
    costStore.setCostToday(new BigDecimal("10.50"), 120);

    BudgetStatus status = service.evaluateBudgetStatus(null, null);
    assertThat(status).isEqualTo(BudgetStatus.BLOCKED);
  }

  @Test
  void testRecordAiDecisionCost_attributesTokensAndCalculatesCost() {
    UUID botId = UUID.randomUUID();
    StructuredTradeDecision decision = new StructuredTradeDecision(
        UUID.randomUUID(), UUID.randomUUID(), "hash123", botId, UUID.randomUUID(), UUID.randomUUID(),
        "fake-llm", "mock-reasoner-v1", TradeAction.NO_ACTION, "BTC/USD", "FLAT",
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        "INTRADAY", "Hold thesis", List.of(), List.of(), List.of(), ValidationStatus.VALIDATED, null,
        1500L, 1000, 200, new BigDecimal("0.001"), now, now.plusSeconds(300)
    );

    AiCostEvent event = service.recordAiDecisionCost(null, decision, "fake-llm", "mock-reasoner-v1", 150L);

    assertThat(event).isNotNull();
    assertThat(event.inputTokens()).isEqualTo(1000);
    assertThat(event.outputTokens()).isEqualTo(200);
    assertThat(event.tokenAttributionJson()).contains("systemInstructions");
    assertThat(costStore.findRecentCostEvents(10)).hasSize(1);
  }

  private static final class MemoryCostStore implements CostStore {
    private final List<AiCostEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, AiBudgetPolicy> policies = Collections.synchronizedMap(new HashMap<>());
    private BigDecimal currentCostToday = BigDecimal.ZERO;
    private long currentReqsToday = 0;

    void setCostToday(BigDecimal cost, long reqs) {
      this.currentCostToday = cost;
      this.currentReqsToday = reqs;
    }

    @Override public AiCostEvent saveCostEvent(AiCostEvent e) { events.add(e); return e; }
    @Override public List<AiCostEvent> findCostEventsByBotId(UUID b, int l) { return events.stream().filter(e -> b.equals(e.botId())).limit(l).toList(); }
    @Override public List<AiCostEvent> findRecentCostEvents(int l) { return events.stream().limit(l).toList(); }
    @Override public void savePricing(AiModelPricing p) {}
    @Override public Optional<AiModelPricing> findActivePricing(String p, String m, Instant w) {
      return Optional.of(new AiModelPricing(UUID.randomUUID(), p, m, new BigDecimal("0.50"), new BigDecimal("1.50"), w.minusSeconds(100), null));
    }
    @Override public void saveBudgetPolicy(AiBudgetPolicy p) { policies.put(p.tier() + "_" + p.targetId(), p); }
    @Override public List<AiBudgetPolicy> findBudgetPolicies() { return new ArrayList<>(policies.values()); }
    @Override public Optional<AiBudgetPolicy> findBudgetPolicy(BudgetTier t, String target) { return Optional.ofNullable(policies.get(t + "_" + target)); }
    @Override public CostSummary getCostSummaryToday(Instant now) { return new CostSummary(currentReqsToday, 0, 0, 0, currentCostToday, BigDecimal.ZERO, currentCostToday, 0, 0, 0); }
    @Override public CostSummary getCostSummaryByBotId(UUID b, Instant now) { return new CostSummary(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0); }
    @Override public CostSummary getCostSummaryByStrategyId(UUID s, Instant now) { return new CostSummary(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0); }
  }
}
