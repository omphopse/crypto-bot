package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.FreshnessSummary;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.ReconciliationContext;
import io.algopilot.agent.context.ResearchEvidenceContext;
import io.algopilot.agent.context.RiskContext;
import io.algopilot.agent.context.SafetySummary;
import io.algopilot.agent.context.ScannerContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.context.TradingContextStore;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.market.scanner.CandidateType;
import io.algopilot.research.model.SecurityStatus;
import java.lang.reflect.Method;
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

class LLMDecisionEngineServiceTest {
  private ContextBuilderService contextBuilder;
  private TradingContextStore contextStore;
  private FakeLLMDecisionProvider provider;
  private StructuredDecisionValidator validator;
  private MemoryStructuredDecisionStore decisionStore;
  private AiCostLimiter costLimiter;
  private AuditEventWriter audit;
  private Clock clock;
  private LLMDecisionEngineService service;

  private UUID botId;
  private UUID contextId;
  private UUID stratVersionId;
  private TradingContext context;
  private Instant now;

  @BeforeEach
  void setUp() {
    contextBuilder = mock(ContextBuilderService.class);
    contextStore = mock(TradingContextStore.class);
    provider = new FakeLLMDecisionProvider();
    validator = new StructuredDecisionValidator();
    decisionStore = new MemoryStructuredDecisionStore();
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    costLimiter = new AiCostLimiter(clock);
    audit = mock(AuditEventWriter.class);

    service = new LLMDecisionEngineService(
        contextBuilder, contextStore, provider, validator, decisionStore, costLimiter, audit, clock
    );

    botId = UUID.randomUUID();
    contextId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();
    now = clock.instant();

    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    StrategyContext strategy = new StrategyContext(UUID.randomUUID(), stratVersionId, "Test Strat", 1, "BTC/USD", "1m", new ObjectMapper().createObjectNode(), "ACTIVE");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    ScannerContext scanner = new ScannerContext(CandidateType.MOMENTUM, new BigDecimal("0.85"), List.of("EMA_CROSS"), "Bullish cross", "CANDIDATE_DETECTED", now);
    ResearchEvidenceContext ev = new ResearchEvidenceContext(UUID.randomUUID(), "BTC", "NEWS", "reuters.com", "ETF Inflows continue", new BigDecimal("0.9"), SecurityStatus.CLEAN, now, true);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    context = new TradingContext(
        contextId, "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, scanner, strategy, portfolio, List.of(), List.of(), risk, recon,
        List.of(ev), null, freshness, safety
    );

    when(contextBuilder.buildContext(botId)).thenReturn(context);
  }

  @Test
  void testAnalyze_producesValidatedDecisionAndPersistsIt() {
    StructuredTradeDecision decision = service.analyzeBot(botId);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
    assertThat(decision.symbol()).isEqualTo("BTC/USD");
    assertThat(decision.confidence()).isEqualByComparingTo("0.85");
    assertThat(decision.isActionable()).isTrue();

    assertThat(decisionStore.findById(decision.id())).isPresent();
    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("DECISION_VALIDATED"), eq("DECISION"), eq(decision.id().toString()), anyMap());
  }

  @Test
  void testAnalyze_whenRateLimited_returnsFailedDecision() {
    AiCostLimiter exhaustedLimiter = mock(AiCostLimiter.class);
    when(exhaustedLimiter.tryAcquire()).thenReturn(false);

    LLMDecisionEngineService limitedService = new LLMDecisionEngineService(
        contextBuilder, contextStore, provider, validator, decisionStore, exhaustedLimiter, audit, clock
    );

    StructuredTradeDecision decision = limitedService.analyzeBot(botId);

    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("AI_RATE_OR_BUDGET_EXCEEDED");
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);

    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("DECISION_FAILED"), eq("DECISION"), eq(decision.id().toString()), anyMap());
  }

  @Test
  void testZeroExecutionAuthority_decisionEngineHasNoTradingMethods() {
    Method[] methods = LLMDecisionEngineService.class.getDeclaredMethods();
    for (Method m : methods) {
      assertThat(m.getName().toLowerCase()).doesNotContain("order");
      assertThat(m.getName().toLowerCase()).doesNotContain("dispatch");
      assertThat(m.getName().toLowerCase()).doesNotContain("execute");
    }
  }

  private static final class MemoryStructuredDecisionStore implements StructuredDecisionStore {
    private final Map<UUID, StructuredTradeDecision> map = Collections.synchronizedMap(new HashMap<>());

    @Override
    public StructuredTradeDecision save(StructuredTradeDecision decision) {
      map.put(decision.id(), decision);
      return decision;
    }

    @Override
    public Optional<StructuredTradeDecision> findLatestByBotId(UUID botId) {
      return map.values().stream().filter(d -> d.botId().equals(botId)).findFirst();
    }

    @Override
    public List<StructuredTradeDecision> findRecentByBotId(UUID botId, int limit) {
      return map.values().stream().filter(d -> d.botId().equals(botId)).limit(limit).toList();
    }

    @Override
    public List<StructuredTradeDecision> findAllRecent(int limit) {
      return map.values().stream().limit(limit).toList();
    }

    @Override
    public Optional<StructuredTradeDecision> findById(UUID id) {
      return Optional.ofNullable(map.get(id));
    }
  }
}
