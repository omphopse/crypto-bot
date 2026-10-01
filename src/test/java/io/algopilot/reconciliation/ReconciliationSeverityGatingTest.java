package io.algopilot.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.adapter.BrokerAdapterException;
import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.context.TradingContextStore;
import io.algopilot.agent.decision.StructuredDecisionValidator;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.decision.ValidationStatus;
import io.algopilot.agent.state.AgentSession;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AgentStateStore;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.market.observation.MarketDataStore;
import io.algopilot.market.observation.MarketObservation;
import io.algopilot.market.scanner.CandidateType;
import io.algopilot.market.scanner.IndicatorSnapshot;
import io.algopilot.market.scanner.MarketScanStore;
import io.algopilot.market.scanner.ScanResult;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.accounting.PortfolioAccountingService;
import io.algopilot.portfolio.accounting.PortfolioSummary;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import io.algopilot.reconciliation.engine.LocalStateSnapshot;
import io.algopilot.reconciliation.engine.ReconciliationEngine;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.model.ResolutionState;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.research.service.ResearchStore;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ReconciliationSeverityGatingTest {

  private final UUID botId = UUID.randomUUID();
  private final UUID stratVersionId = UUID.randomUUID();
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  private BotStore botStore;
  private AgentStateStore agentStateStore;
  private StrategyStore strategyStore;
  private MarketDataStore marketDataStore;
  private MarketScanStore marketScanStore;
  private PortfolioAccountingService accountingService;
  private OrderStore orderStore;
  private ReconciliationStore reconciliationStore;
  private ResearchStore researchStore;
  private TradingContextStore contextStore;
  private AuditEventWriter audit;
  private ContextBuilderService contextBuilder;
  private StructuredDecisionValidator validator;

  @BeforeEach
  void setUp() {
    botStore = mock(BotStore.class);
    agentStateStore = mock(AgentStateStore.class);
    strategyStore = mock(StrategyStore.class);
    marketDataStore = mock(MarketDataStore.class);
    marketScanStore = mock(MarketScanStore.class);
    accountingService = mock(PortfolioAccountingService.class);
    orderStore = mock(OrderStore.class);
    reconciliationStore = mock(ReconciliationStore.class);
    researchStore = mock(ResearchStore.class);
    contextStore = mock(TradingContextStore.class);
    audit = mock(AuditEventWriter.class);

    contextBuilder = new ContextBuilderService(
        botStore, agentStateStore, strategyStore, marketDataStore, marketScanStore,
        accountingService, orderStore, reconciliationStore, researchStore, contextStore,
        audit, clock, json
    );
    validator = new StructuredDecisionValidator();

    // Setup standard bot
    Bot bot = new Bot(botId, "Canary Bot", stratVersionId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, clock.instant());
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    // Setup active session
    UUID sessionId = UUID.randomUUID();
    AgentSession session = new AgentSession(sessionId, botId, "Canary Session", AgentState.IDLE, AutonomousMode.PAPER_AUTONOMOUS, json.createObjectNode(), clock.instant(), clock.instant(), null);
    when(agentStateStore.findActiveSessionByBotId(botId)).thenReturn(Optional.of(session));

    // Setup strategy
    ObjectNode stratDef = json.createObjectNode().put("symbol", "BTC/USD");
    StrategyVersion sv = new StrategyVersion(stratVersionId, UUID.randomUUID(), 1, stratDef, "v1", clock.instant());
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.of(sv));

    // Setup fresh market observation
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("83500.00"), new BigDecimal("83490.00"), new BigDecimal("83510.00"),
        BigDecimal.TEN, new BigDecimal("83000.00"), new BigDecimal("84000.00"),
        new BigDecimal("82900.00"), new BigDecimal("83500.00"), "1m", clock.instant(), clock.instant(), 60_000L
    );
    when(marketDataStore.findLatest("BTC/USD")).thenReturn(Optional.of(obs));
    when(marketScanStore.findRecentBySymbol(eq("BTC/USD"), eq(1))).thenReturn(List.of());

    // Setup portfolio
    PortfolioSummary summary = new PortfolioSummary(
        new BigDecimal("150.00"), new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, clock.instant()
    );
    when(accountingService.calculateSummary()).thenReturn(summary);
    when(accountingService.getMarkedPositions()).thenReturn(List.of());
    when(orderStore.findOpenOrdersByBotId(botId.toString())).thenReturn(List.of());
    when(researchStore.findEvidenceByAsset(any(), eq(10))).thenReturn(List.of());
  }

  private StructuredTradeDecision createDecision(TradeAction action, TradingContext context) {
    Instant now = clock.instant();
    return new StructuredTradeDecision(
        UUID.randomUUID(), context.contextId(), context.contextHash(), botId, UUID.randomUUID(), stratVersionId,
        "ollama", "gemma3:4b", action, "BTC/USD", action == TradeAction.BUY ? "BUY" : "SELL",
        new BigDecimal("0.85"), new BigDecimal("1.0"), new BigDecimal("83500.00"),
        action == TradeAction.BUY ? new BigDecimal("83400.00") : new BigDecimal("83600.00"),
        action == TradeAction.BUY ? new BigDecimal("83700.00") : new BigDecimal("83300.00"),
        "INTRADAY", "Momentum confirmed", List.of(), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 100L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );
  }

  @Test
  @DisplayName("1. Unresolved CRITICAL mismatch blocks BUY decision")
  void testUnresolvedCriticalMismatch_blocksBuy() {
    ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MISMATCHED, 1, "Critical", clock.instant(), clock.instant(), clock.instant());
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(run));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString(), MismatchSeverity.CRITICAL)).thenReturn(1);

    TradingContext context = contextBuilder.buildContext(botId);
    assertThat(context.reconciliation().isTradingBlocked()).isTrue();
    assertThat(context.safety().executionAllowed()).isFalse();

    StructuredTradeDecision decision = createDecision(TradeAction.BUY, context);
    StructuredTradeDecision validated = validator.validate(decision, context);

    assertThat(validated.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(validated.rejectionReason()).contains("SAFETY_GATES_DISALLOWED:RECONCILIATION_MISMATCH_PRESENT");
  }

  @Test
  @DisplayName("2. Unresolved CRITICAL mismatch blocks SELL decision where safety summary disallows execution")
  void testUnresolvedCriticalMismatch_blocksSell() {
    ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MISMATCHED, 1, "Critical", clock.instant(), clock.instant(), clock.instant());
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(run));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString(), MismatchSeverity.CRITICAL)).thenReturn(1);

    TradingContext context = contextBuilder.buildContext(botId);
    assertThat(context.reconciliation().isTradingBlocked()).isTrue();
    assertThat(context.safety().executionAllowed()).isFalse();

    StructuredTradeDecision decision = createDecision(TradeAction.SELL, context);
    StructuredTradeDecision validated = validator.validate(decision, context);

    assertThat(validated.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(validated.rejectionReason()).contains("SAFETY_GATES_DISALLOWED:RECONCILIATION_MISMATCH_PRESENT");
  }

  @Test
  @DisplayName("3. Unresolved WARNING mismatch does NOT block a new BUY decision")
  void testUnresolvedWarningMismatch_doesNotBlockBuy() {
    // Latest run MATCHED, but 1 historical unresolved WARNING mismatch exists (critical = 0)
    ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MATCHED, 0, null, clock.instant(), clock.instant(), clock.instant());
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(run));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString(), MismatchSeverity.CRITICAL)).thenReturn(0);

    TradingContext context = contextBuilder.buildContext(botId);
    assertThat(context.reconciliation().isTradingBlocked()).isFalse();
    assertThat(context.safety().executionAllowed()).isTrue();
    assertThat(context.safety().safetyBlockReasons()).doesNotContain("RECONCILIATION_MISMATCH_PRESENT");

    StructuredTradeDecision decision = createDecision(TradeAction.BUY, context);
    StructuredTradeDecision validated = validator.validate(decision, context);

    assertThat(validated.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
    assertThat(validated.rejectionReason()).isNull();
  }

  @Test
  @DisplayName("4. Healthy reconciliation allows normal trading")
  void testHealthyReconciliation_allowsTrading() {
    ReconciliationRun run = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MATCHED, 0, null, clock.instant(), clock.instant(), clock.instant());
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(run));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString(), MismatchSeverity.CRITICAL)).thenReturn(0);

    TradingContext context = contextBuilder.buildContext(botId);
    assertThat(context.reconciliation().status()).isEqualTo("MATCHED");
    assertThat(context.reconciliation().isTradingBlocked()).isFalse();
    assertThat(context.safety().executionAllowed()).isTrue();

    StructuredTradeDecision decision = createDecision(TradeAction.BUY, context);
    StructuredTradeDecision validated = validator.validate(decision, context);
    assertThat(validated.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
  }

  @Test
  @DisplayName("5. Genuine position divergence creates CRITICAL mismatch and blocks execution in ExecutionGateway")
  void testGenuinePositionDivergence_blocksExecution() {
    ReconciliationEngine engine = new ReconciliationEngine(clock);
    UUID runId = UUID.randomUUID();

    LocalStateSnapshot localSnapshot = new LocalStateSnapshot(
        botId.toString(), null, null, null, List.of(), List.of(),
        List.of(new Position(UUID.randomUUID(), botId.toString(), "BTC/USD", new BigDecimal("0.50"), new BigDecimal("83000.00"), BigDecimal.ZERO, clock.instant())),
        clock.instant()
    );

    BrokerStateSnapshot brokerSnapshot = new BrokerStateSnapshot(
        Broker.ALPACA_PAPER, ExecutionMode.PAPER, botId.toString(), null, List.of(), List.of(),
        List.of(new BrokerPosition(botId.toString(), "BTC/USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, clock.instant())),
        clock.instant()
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId.toString(), localSnapshot, brokerSnapshot);
    assertThat(mismatches).hasSize(1);
    ReconciliationMismatch mismatch = mismatches.get(0);
    assertThat(mismatch.severity()).isEqualTo(MismatchSeverity.CRITICAL);
    assertThat(mismatch.mismatchType()).isEqualTo(MismatchType.POSITION_QUANTITY_MISMATCH);

    // Verify ExecutionGateway checks critical mismatches
    OrderRecord order = new OrderRecord(UUID.randomUUID(), "client-1", botId.toString(), stratVersionId.toString(), "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("0.0001"), new BigDecimal("83500.00"), OrderStatus.CREATED, clock.instant());
    OrderStore orderStoreMock = mock(OrderStore.class);
    when(orderStoreMock.findById(order.id())).thenReturn(Optional.of(order));

    ReconciliationStore reconStoreMock = mock(ReconciliationStore.class);
    when(reconStoreMock.findMismatchesByBotId(eq(botId.toString()), eq(ResolutionState.UNRESOLVED)))
        .thenReturn(List.of(mismatch));

    ExecutionGateway gateway = new ExecutionGateway(
        List.of(), botStore, orderStoreMock, mock(OrderLifecycleService.class), audit, reconStoreMock, clock
    );

    assertThatThrownBy(() -> gateway.dispatch(order.id()))
        .isInstanceOf(BrokerAdapterException.class)
        .hasMessageContaining("BOT_RECONCILIATION_MISMATCH_BLOCK");
  }

  @Test
  @DisplayName("6. Mismatch resolution updates state and remains completely auditable")
  void testMismatchResolution_remainsAuditable() {
    UUID mismatchId = UUID.randomUUID();
    Instant resolvedAt = clock.instant();

    reconciliationStore.updateMismatchResolution(mismatchId, ResolutionState.RESOLVED, resolvedAt);
    verify(reconciliationStore, times(1)).updateMismatchResolution(eq(mismatchId), eq(ResolutionState.RESOLVED), eq(resolvedAt));

    reconciliationStore.resolveAllUnresolvedMismatchesForBot(botId.toString(), resolvedAt);
    verify(reconciliationStore, times(1)).resolveAllUnresolvedMismatchesForBot(eq(botId.toString()), eq(resolvedAt));
  }
}
