package io.algopilot.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.accounting.PortfolioAccountingService;
import io.algopilot.portfolio.accounting.PortfolioSummary;
import io.algopilot.portfolio.accounting.PositionMark;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.model.ReconciliationStatus;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.model.SecurityStatus;
import io.algopilot.research.service.ResearchStore;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
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

class ContextBuilderServiceTest {
  private BotStore botStore;
  private AgentStateStore agentStateStore;
  private StrategyStore strategyStore;
  private MarketDataStore marketDataStore;
  private MarketScanStore marketScanStore;
  private PortfolioAccountingService accountingService;
  private OrderStore orderStore;
  private ReconciliationStore reconciliationStore;
  private ResearchStore researchStore;
  private MemoryTradingContextStore contextStore;
  private AuditEventWriter audit;
  private Clock clock;
  private ObjectMapper json;
  private ContextBuilderService service;

  private UUID botId;
  private UUID stratVersionId;
  private Bot bot;

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
    contextStore = new MemoryTradingContextStore();
    audit = mock(AuditEventWriter.class);
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    json = new ObjectMapper();

    service = new ContextBuilderService(
        botStore, agentStateStore, strategyStore, marketDataStore, marketScanStore,
        accountingService, orderStore, reconciliationStore, researchStore, contextStore, audit, clock, json
    );

    botId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();
    bot = new Bot(botId, "Canary Agent", stratVersionId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, clock.instant());
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));
  }

  @Test
  void testBuildContext_assemblesCompleteValidContext() {
    Instant now = clock.instant();

    // 1. Agent Session
    UUID sessionId = UUID.randomUUID();
    AgentSession session = new AgentSession(sessionId, botId, "Canary Bot", AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, json.createObjectNode(), now, now, null);
    when(agentStateStore.findActiveSessionByBotId(botId)).thenReturn(Optional.of(session));

    // 2. Strategy Version
    ObjectNode def = json.createObjectNode().put("symbol", "BTC/USD").put("timeframe", "1m");
    StrategyVersion sv = new StrategyVersion(stratVersionId, UUID.randomUUID(), 1, def, "initial", now);
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.of(sv));

    // 3. Market Data
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"),
        new BigDecimal("15.0"), new BigDecimal("59900.00"), new BigDecimal("60100.00"),
        new BigDecimal("59850.00"), new BigDecimal("60000.00"), "1m", now.minusSeconds(10), now, 60_000L
    );
    when(marketDataStore.findLatest("BTC/USD")).thenReturn(Optional.of(obs));

    // 4. Scanner Result
    IndicatorSnapshot ind = new IndicatorSnapshot("BTC/USD", "1m", now, new BigDecimal("59950.00"), new BigDecimal("59800.00"), null, null, new BigDecimal("62.50"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("150.00"), null, null, null, new BigDecimal("10.0"), new BigDecimal("15.0"), new BigDecimal("0.50"), new BigDecimal("0.0025"), true);
    ScanResult scan = new ScanResult(UUID.randomUUID(), sessionId, botId, "BTC/USD", "1m", "ALPACA_PAPER", CandidateType.MOMENTUM, List.of("EMA_CROSS"), ind, obs, new BigDecimal("0.85"), "Bullish trend", "CANDIDATE_DETECTED", now);
    when(marketScanStore.findRecentBySymbol("BTC/USD", 1)).thenReturn(List.of(scan));

    // 5. Portfolio Accounting
    PositionMark pos = new PositionMark(UUID.randomUUID(), botId.toString(), "BTC/USD", new BigDecimal("0.50"), new BigDecimal("58000.00"), new BigDecimal("60000.00"), new BigDecimal("29000.00"), new BigDecimal("30000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, now);
    PortfolioSummary summary = new PortfolioSummary(new BigDecimal("100000.00"), new BigDecimal("71000.00"), new BigDecimal("29000.00"), new BigDecimal("30000.00"), new BigDecimal("30000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), new BigDecimal("101000.00"), BigDecimal.ZERO, new BigDecimal("30000.00"), new BigDecimal("29.70"), now);
    when(accountingService.calculateSummary()).thenReturn(summary);
    when(accountingService.getMarkedPositions()).thenReturn(List.of(pos));

    // 6. Orders
    when(orderStore.findOpenOrdersByBotId(botId.toString())).thenReturn(List.of());

    // 7. Reconciliation
    ReconciliationRun recon = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MATCHED, 0, null, now, now, now);
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(recon));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString())).thenReturn(0);

    // 8. Research Evidence
    ResearchEvidence ev = new ResearchEvidence(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "reuters.com", "BTC", "NEWS", "ETF Inflows continue", now, now, new BigDecimal("0.90"), SecurityStatus.CLEAN, "hash123");
    when(researchStore.findEvidenceByAsset("BTC", 10)).thenReturn(List.of(ev));

    // Execute Context Assembly
    TradingContext context = service.buildContext(botId);

    assertThat(context).isNotNull();
    assertThat(context.contextHash()).isNotEmpty();
    assertThat(context.market().lastPrice()).isEqualByComparingTo("60000.00");
    assertThat(context.portfolio().portfolioEquity()).isEqualByComparingTo("101000.00");
    assertThat(context.positions()).hasSize(1);
    assertThat(context.research()).hasSize(1);
    assertThat(context.research().get(0).isUntrustedExternalData()).isTrue();
    assertThat(context.safety().executionAllowed()).isTrue();

    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("CONTEXT_BUILD_COMPLETED"), eq("CONTEXT"), eq(context.contextId().toString()), anyMap());
  }

  @Test
  void testStaleMarketData_blocksExecution() {
    Instant now = clock.instant();
    Instant oldTime = now.minusSeconds(400); // > 5 minutes stale

    MarketObservation staleObs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"),
        BigDecimal.TEN, new BigDecimal("59900.00"), new BigDecimal("60100.00"),
        new BigDecimal("59850.00"), new BigDecimal("60000.00"), "1m", oldTime, now, 60_000L
    );
    when(marketDataStore.findLatest("BTC/USD")).thenReturn(Optional.of(staleObs));

    PortfolioSummary summary = new PortfolioSummary(new BigDecimal("100000.00"), new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now);
    when(accountingService.calculateSummary()).thenReturn(summary);
    when(accountingService.getMarkedPositions()).thenReturn(List.of());

    TradingContext context = service.buildContext(botId);

    assertThat(context.freshness().overallStatus()).isEqualTo(FreshnessStatus.STALE);
    assertThat(context.safety().marketDataFresh()).isFalse();
    assertThat(context.safety().executionAllowed()).isFalse();
    assertThat(context.safety().safetyBlockReasons()).contains("MARKET_DATA_STALE");
  }

  @Test
  void testReconciliationMismatch_blocksExecution() {
    Instant now = clock.instant();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"),
        BigDecimal.TEN, new BigDecimal("59900.00"), new BigDecimal("60100.00"),
        new BigDecimal("59850.00"), new BigDecimal("60000.00"), "1m", now, now, 60_000L
    );
    when(marketDataStore.findLatest("BTC/USD")).thenReturn(Optional.of(obs));

    PortfolioSummary summary = new PortfolioSummary(new BigDecimal("100000.00"), new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now);
    when(accountingService.calculateSummary()).thenReturn(summary);
    when(accountingService.getMarkedPositions()).thenReturn(List.of());

    // Active reconciliation critical mismatch!
    ReconciliationRun mismatchRecon = new ReconciliationRun(UUID.randomUUID(), botId.toString(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, ReconciliationStatus.MISMATCHED, 2, "Discrepancy", now, now, now);
    when(reconciliationStore.findLatestRunByBotId(botId.toString())).thenReturn(Optional.of(mismatchRecon));
    when(reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString())).thenReturn(2);

    TradingContext context = service.buildContext(botId);

    assertThat(context.reconciliation().isTradingBlocked()).isTrue();
    assertThat(context.safety().reconciliationHealthy()).isFalse();
    assertThat(context.safety().executionAllowed()).isFalse();
    assertThat(context.safety().safetyBlockReasons()).contains("RECONCILIATION_MISMATCH_PRESENT");
  }

  @Test
  void testZeroExecutionAuthority_contextBuilderHasNoTradingPaths() {
    Method[] methods = ContextBuilderService.class.getDeclaredMethods();
    for (Method m : methods) {
      assertThat(m.getName().toLowerCase()).doesNotContain("order");
      assertThat(m.getName().toLowerCase()).doesNotContain("dispatch");
      assertThat(m.getName().toLowerCase()).doesNotContain("buy");
      assertThat(m.getName().toLowerCase()).doesNotContain("sell");
    }
  }

  private static final class MemoryTradingContextStore implements TradingContextStore {
    private final Map<UUID, TradingContext> contexts = Collections.synchronizedMap(new HashMap<>());

    @Override
    public TradingContext save(TradingContext context) {
      contexts.put(context.contextId(), context);
      return context;
    }

    @Override
    public Optional<TradingContext> findLatestByBotId(UUID botId) {
      return contexts.values().stream().filter(c -> c.botId().equals(botId)).findFirst();
    }

    @Override
    public List<TradingContext> findRecentByBotId(UUID botId, int limit) {
      return contexts.values().stream().filter(c -> c.botId().equals(botId)).limit(limit).toList();
    }

    @Override
    public Optional<TradingContext> findById(UUID id) {
      return Optional.ofNullable(contexts.get(id));
    }
  }
}
