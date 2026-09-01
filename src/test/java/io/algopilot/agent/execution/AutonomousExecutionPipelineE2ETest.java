package io.algopilot.agent.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.adapter.BrokerOrderAdapter;
import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.adapter.OrderSubmissionResult;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.FreshnessSummary;
import io.algopilot.agent.context.IndicatorContext;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.ReconciliationContext;
import io.algopilot.agent.context.ResearchEvidenceContext;
import io.algopilot.agent.context.RiskContext;
import io.algopilot.agent.context.SafetySummary;
import io.algopilot.agent.context.ScannerContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.context.TradingContextStore;
import io.algopilot.agent.decision.AiCostLimiter;
import io.algopilot.agent.decision.FakeLLMDecisionProvider;
import io.algopilot.agent.decision.LLMDecisionEngineService;
import io.algopilot.agent.decision.StructuredDecisionStore;
import io.algopilot.agent.decision.StructuredDecisionValidator;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.market.scanner.CandidateType;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.order.OrderService;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.reconciliation.service.ReconciliationService;
import io.algopilot.research.model.SecurityStatus;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
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

class AutonomousExecutionPipelineE2ETest {
  private ContextBuilderService contextBuilder;
  private LLMDecisionEngineService decisionEngine;
  private StrategyValidationService strategyValidator;
  private OrderService orderService;
  private ExecutionGateway executionGateway;
  private ReconciliationService reconciliationService;
  private MemoryAutonomousExecutionStore executionStore;
  private AuditEventWriter audit;
  private Clock clock;
  private ObjectMapper json;
  private AutonomousExecutionOrchestrator orchestrator;

  private BotStore botStore;
  private StrategyStore strategyStore;
  private BrokerOrderAdapter mockAdapter;
  private UUID botId;
  private UUID stratVersionId;
  private UUID stratId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();
    json = new ObjectMapper();
    audit = mock(AuditEventWriter.class);

    botStore = mock(BotStore.class);
    strategyStore = mock(StrategyStore.class);
    mockAdapter = mock(BrokerOrderAdapter.class);
    when(mockAdapter.broker()).thenReturn(Broker.ALPACA_PAPER);
    when(mockAdapter.supportedMode()).thenReturn(ExecutionMode.PAPER);

    botId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();
    stratId = UUID.randomUUID();

    Bot bot = new Bot(botId, "Canary Alpaca", stratVersionId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    ObjectNode def = json.createObjectNode().put("symbol", "BTC/USD");
    StrategyVersion sv = new StrategyVersion(stratVersionId, stratId, 1, def, "initial", now);
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.of(sv));

    // Decision Engine wiring
    contextBuilder = mock(ContextBuilderService.class);
    TradingContextStore contextStore = mock(TradingContextStore.class);
    FakeLLMDecisionProvider provider = new FakeLLMDecisionProvider();
    StructuredDecisionValidator decisionValidator = new StructuredDecisionValidator();
    MemoryStructuredDecisionStore decisionStore = new MemoryStructuredDecisionStore();
    AiCostLimiter costLimiter = new AiCostLimiter(clock);

    decisionEngine = new LLMDecisionEngineService(
        contextBuilder, contextStore, provider, decisionValidator, decisionStore, costLimiter, audit, clock
    );

    executionStore = new MemoryAutonomousExecutionStore();
    strategyValidator = new StrategyValidationService(botStore, strategyStore, executionStore, audit, clock);

    orderService = mock(OrderService.class);
    executionGateway = mock(ExecutionGateway.class);
    reconciliationService = mock(ReconciliationService.class);

    orchestrator = new AutonomousExecutionOrchestrator(
        contextBuilder, decisionEngine, strategyValidator, orderService,
        executionGateway, reconciliationService, executionStore, audit, clock
    );
  }

  @Test
  void testAutonomousExecutionPipeline_paperAutonomous_fullFlowE2E() {
    UUID contextId = UUID.randomUUID();
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    StrategyContext strategy = new StrategyContext(stratId, stratVersionId, "Canary Strategy", 1, "BTC/USD", "1m", json.createObjectNode(), "ACTIVE");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    ScannerContext scanner = new ScannerContext(CandidateType.MOMENTUM, new BigDecimal("0.85"), List.of("EMA_CROSS"), "Bullish cross", "CANDIDATE_DETECTED", now);
    ResearchEvidenceContext ev = new ResearchEvidenceContext(UUID.randomUUID(), "BTC", "NEWS", "reuters.com", "ETF Inflows continue", new BigDecimal("0.9"), SecurityStatus.CLEAN, now, true);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    TradingContext context = new TradingContext(
        contextId, "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, scanner, strategy, portfolio, List.of(), List.of(), risk, recon,
        List.of(ev), null, freshness, safety
    );

    when(contextBuilder.buildContext(botId)).thenReturn(context);

    // Mock Order Creation
    UUID orderId = UUID.randomUUID();
    OrderRecord order = new OrderRecord(orderId, "auto-12345", botId.toString(), stratVersionId.toString(), "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("0.10"), new BigDecimal("60000.00"), OrderStatus.CREATED, now);
    when(orderService.create(any())).thenReturn(order);

    // Mock Dispatch
    when(executionGateway.dispatch(orderId)).thenReturn(new OrderSubmissionResult("auto-12345", "alpaca-ord-123", OrderStatus.ACKNOWLEDGED, now, Map.of()));

    // Execute Autonomous Cycle
    AutonomousExecutionResult result = orchestrator.runCycle(botId);

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo("EXECUTED");
    assertThat(result.orderId()).isEqualTo(orderId);
    assertThat(result.detail()).contains("alpaca-ord-123");

    verify(orderService, times(1)).create(any());
    verify(executionGateway, times(1)).dispatch(orderId);
    verify(reconciliationService, times(1)).reconcile(botId.toString());
  }

  @Test
  void testAutonomousExecutionPipeline_observeOnly_noOrdersExecuted() {
    UUID contextId = UUID.randomUUID();
    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    StrategyContext strategy = new StrategyContext(stratId, stratVersionId, "Canary Strategy", 1, "BTC/USD", "1m", json.createObjectNode(), "ACTIVE");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    ScannerContext scanner = new ScannerContext(CandidateType.MOMENTUM, new BigDecimal("0.85"), List.of("EMA_CROSS"), "Bullish cross", "CANDIDATE_DETECTED", now);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    // MODE = OBSERVE_ONLY
    TradingContext context = new TradingContext(
        contextId, "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.OBSERVE_ONLY, market,
        null, scanner, strategy, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );

    when(contextBuilder.buildContext(botId)).thenReturn(context);

    // Execute Autonomous Cycle
    AutonomousExecutionResult result = orchestrator.runCycle(botId);

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo("OBSERVE_ONLY_RECORDED");
    assertThat(result.orderId()).isNull();

    // Verify zero execution calls!
    verify(orderService, never()).create(any());
    verify(executionGateway, never()).dispatch(any());
  }

  private static final class MemoryAutonomousExecutionStore implements AutonomousExecutionStore {
    private final Map<UUID, ValidatedTradeIntent> intents = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, StrategyValidationResult> validations = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, AutonomousExecutionResult> executions = Collections.synchronizedMap(new HashMap<>());

    @Override public ValidatedTradeIntent saveIntent(ValidatedTradeIntent intent) { intents.put(intent.intentId(), intent); return intent; }
    @Override public StrategyValidationResult saveValidation(StrategyValidationResult result) { validations.put(result.id(), result); return result; }
    @Override public AutonomousExecutionResult saveExecution(AutonomousExecutionResult execution) { executions.put(execution.id(), execution); return execution; }
    @Override public List<AutonomousExecutionResult> findRecentExecutionsByBotId(UUID botId, int limit) { return executions.values().stream().limit(limit).toList(); }
    @Override public Optional<AutonomousExecutionResult> findLatestExecutionByBotId(UUID botId) { return executions.values().stream().findFirst(); }
  }

  private static final class MemoryStructuredDecisionStore implements StructuredDecisionStore {
    private final Map<UUID, StructuredTradeDecision> map = Collections.synchronizedMap(new HashMap<>());
    @Override public StructuredTradeDecision save(StructuredTradeDecision d) { map.put(d.id(), d); return d; }
    @Override public Optional<StructuredTradeDecision> findLatestByBotId(UUID botId) { return map.values().stream().filter(d -> d.botId().equals(botId)).findFirst(); }
    @Override public List<StructuredTradeDecision> findRecentByBotId(UUID botId, int limit) { return map.values().stream().filter(d -> d.botId().equals(botId)).limit(limit).toList(); }
    @Override public List<StructuredTradeDecision> findAllRecent(int limit) { return map.values().stream().limit(limit).toList(); }
    @Override public Optional<StructuredTradeDecision> findById(UUID id) { return Optional.ofNullable(map.get(id)); }
  }
}
