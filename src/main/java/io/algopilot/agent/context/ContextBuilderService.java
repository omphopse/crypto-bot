package io.algopilot.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.state.AgentSession;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AgentStateStore;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.market.observation.MarketDataStore;
import io.algopilot.market.observation.MarketObservation;
import io.algopilot.market.scanner.CandidateType;
import io.algopilot.market.scanner.MarketScanStore;
import io.algopilot.market.scanner.ScanResult;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.accounting.PortfolioAccountingService;
import io.algopilot.portfolio.accounting.PortfolioSummary;
import io.algopilot.portfolio.accounting.PositionMark;
import io.algopilot.reconciliation.model.ReconciliationRun;
import io.algopilot.reconciliation.persistence.ReconciliationStore;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.service.ResearchStore;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContextBuilderService {
  private static final Logger log = LoggerFactory.getLogger(ContextBuilderService.class);

  private final BotStore botStore;
  private final AgentStateStore agentStateStore;
  private final StrategyStore strategyStore;
  private final MarketDataStore marketDataStore;
  private final MarketScanStore marketScanStore;
  private final PortfolioAccountingService accountingService;
  private final OrderStore orderStore;
  private final ReconciliationStore reconciliationStore;
  private final ResearchStore researchStore;
  private final TradingContextStore contextStore;
  private final AuditEventWriter audit;
  private final Clock clock;
  private final ObjectMapper json;

  public ContextBuilderService(
      BotStore botStore,
      AgentStateStore agentStateStore,
      StrategyStore strategyStore,
      MarketDataStore marketDataStore,
      MarketScanStore marketScanStore,
      PortfolioAccountingService accountingService,
      OrderStore orderStore,
      ReconciliationStore reconciliationStore,
      ResearchStore researchStore,
      TradingContextStore contextStore,
      AuditEventWriter audit,
      Clock clock,
      ObjectMapper json
  ) {
    this.botStore = botStore;
    this.agentStateStore = agentStateStore;
    this.strategyStore = strategyStore;
    this.marketDataStore = marketDataStore;
    this.marketScanStore = marketScanStore;
    this.accountingService = accountingService;
    this.orderStore = orderStore;
    this.reconciliationStore = reconciliationStore;
    this.researchStore = researchStore;
    this.contextStore = contextStore;
    this.audit = audit;
    this.clock = clock;
    this.json = json;
  }

  public TradingContext buildContext(UUID botId) {
    Instant now = clock.instant();
    Bot bot = botStore.findById(botId)
        .orElseThrow(() -> new IllegalArgumentException("Bot not found with ID: " + botId));

    String provider = bot.broker().name();
    String environment = bot.executionMode().name();

    // 1. Strategy Context & Target Symbol Extraction
    StrategyContext strategyContext;
    String symbol = "BTC/USD";
    String timeframe = "1m";
    JsonNode params = json.createObjectNode();

    Optional<StrategyVersion> stratOpt = strategyStore.findVersionById(bot.strategyVersionId());
    if (stratOpt.isPresent()) {
      StrategyVersion sv = stratOpt.get();
      if (sv.definition() != null) {
        if (sv.definition().has("symbol")) symbol = sv.definition().get("symbol").asText();
        if (sv.definition().has("timeframe")) timeframe = sv.definition().get("timeframe").asText();
        if (sv.definition().has("parameters")) params = sv.definition().get("parameters");
        else params = sv.definition();
      }
      strategyContext = new StrategyContext(sv.strategyId(), sv.id(), bot.name(), sv.versionNumber(), symbol, timeframe, params, "ACTIVE");
    } else {
      strategyContext = new StrategyContext(UUID.randomUUID(), bot.strategyVersionId(), bot.name(), 1, symbol, timeframe, params, "ACTIVE");
    }

    // 2. Agent Session & State
    Optional<AgentSession> sessionOpt = agentStateStore.findActiveSessionByBotId(botId);
    UUID sessionId = sessionOpt.map(AgentSession::id).orElse(UUID.randomUUID());
    AgentState agentState = sessionOpt.map(AgentSession::currentState).orElse(AgentState.IDLE);
    AutonomousMode autonomousMode = sessionOpt.map(AgentSession::mode).orElse(AutonomousMode.OBSERVE_ONLY);

    // 3. Market Context
    Optional<MarketObservation> obsOpt = marketDataStore.findLatest(symbol);
    MarketContext marketContext;
    long marketFreshnessMs = 0;
    FreshnessStatus marketFreshnessStatus = FreshnessStatus.UNAVAILABLE;

    if (obsOpt.isPresent()) {
      MarketObservation o = obsOpt.get();
      marketFreshnessMs = Math.max(0, now.toEpochMilli() - o.marketTimestamp().toEpochMilli());
      if (marketFreshnessMs <= 60_000L) marketFreshnessStatus = FreshnessStatus.FRESH;
      else if (marketFreshnessMs <= 300_000L) marketFreshnessStatus = FreshnessStatus.AGING;
      else marketFreshnessStatus = FreshnessStatus.STALE;

      marketContext = new MarketContext(
          o.symbol(), o.provider(), o.environment(), o.lastPrice(), o.bid(), o.ask(), o.spread(),
          o.volume(), o.openPrice(), o.highPrice(), o.lowPrice(), o.closePrice(), o.timeframe(),
          o.marketTimestamp(), o.receivedAt(), marketFreshnessMs, marketFreshnessStatus, o.validationStatus()
      );
    } else {
      marketContext = new MarketContext(
          symbol, provider, environment, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, timeframe,
          now, now, 0, FreshnessStatus.UNAVAILABLE, "UNAVAILABLE"
      );
    }

    // 4. Scanner Context & Indicator Snapshot
    List<ScanResult> scans = marketScanStore.findRecentBySymbol(symbol, 1);
    ScannerContext scannerContext;
    IndicatorContext indicatorContext;

    if (!scans.isEmpty()) {
      ScanResult s = scans.get(0);
      scannerContext = new ScannerContext(s.candidateType(), s.confidenceScore(), s.triggerConditions(), s.reason(), s.status(), s.timestamp());
      var ind = s.indicatorSnapshot();
      if (ind != null) {
        indicatorContext = new IndicatorContext(
            symbol, s.timeframe(), ind.emaFast(), ind.emaSlow(), ind.sma50(), ind.sma200(), ind.rsi14(),
            ind.macd(), ind.macdSignal(), ind.macdHistogram(), ind.atr14(), ind.bbUpper(), ind.bbMiddle(),
            ind.bbLower(), ind.averageVolume(), ind.currentVolume(), ind.priceChangePct(), ind.volatility(),
            ind.isWarmedUp(), s.timestamp()
        );
      } else {
        indicatorContext = emptyIndicatorContext(symbol, now);
      }
    } else {
      scannerContext = new ScannerContext(CandidateType.NO_CANDIDATE, BigDecimal.ZERO, List.of(), "No scanner run", "UNAVAILABLE", now);
      indicatorContext = emptyIndicatorContext(symbol, now);
    }

    // 5. Authoritative Portfolio Accounting
    PortfolioSummary summary = accountingService.calculateSummary();
    List<PositionMark> markedPositions = accountingService.getMarkedPositions();

    PortfolioContext portfolioContext = new PortfolioContext(
        summary.startingCapital(), summary.cash(), summary.marketExposure(), summary.costBasisExposure(),
        summary.grossExposure(), summary.pendingOrderNotional(), summary.totalReservedExposure(),
        summary.realizedPnl(), summary.unrealizedPnl(), summary.totalNetPnl(), summary.cumulativeFees(),
        summary.portfolioEquity(), summary.riskUtilizationPercent(), now
    );

    // 6. Positions
    List<PositionContext> positionContexts = new ArrayList<>();
    for (PositionMark pm : markedPositions) {
      String side = pm.quantity().signum() >= 0 ? "LONG" : "SHORT";
      positionContexts.add(new PositionContext(
          pm.symbol(), side, pm.quantity(), pm.averageEntryPrice(), pm.currentMarketPrice(),
          pm.marketValue(), pm.costBasis(), pm.unrealizedPnl(), pm.realizedPnl()
      ));
    }

    // 7. Open Orders
    List<OrderRecord> openOrders = orderStore.findOpenOrdersByBotId(botId.toString());
    List<OpenOrderContext> openOrderContexts = new ArrayList<>();
    for (OrderRecord ord : openOrders) {
      if (ord.status() != OrderStatus.FILLED && ord.status() != OrderStatus.CANCELLED && ord.status() != OrderStatus.REJECTED) {
        BigDecimal notional = ord.referencePrice().multiply(ord.quantity());
        openOrderContexts.add(new OpenOrderContext(
            ord.id(), ord.clientOrderId(), ord.symbol(), ord.side().name(),
            ord.quantity(), ord.referencePrice(), ord.status().name(), notional, ord.createdAt()
        ));
      }
    }

    // 8. Risk Context
    boolean isEmergency = bot.status() == BotStatus.EMERGENCY_STOPPED;
    final String targetSymbol = symbol;
    BigDecimal symbolExposure = markedPositions.stream()
        .filter(p -> p.symbol().equalsIgnoreCase(targetSymbol))
        .map(PositionMark::marketValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    RiskContext riskContext = new RiskContext(
        isEmergency ? "EMERGENCY_STOPPED" : "ACTIVE",
        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
        symbolExposure, summary.portfolioEquity().multiply(new BigDecimal("0.10")),
        summary.grossExposure(), summary.portfolioEquity().multiply(new BigDecimal("0.50")),
        isEmergency, false, isEmergency
    );

    // 9. Reconciliation Context
    Optional<ReconciliationRun> reconOpt = reconciliationStore.findLatestRunByBotId(botId.toString());
    int unresolvedMismatches = reconciliationStore.countUnresolvedMismatchesByBotId(botId.toString());
    ReconciliationContext reconContext;

    if (reconOpt.isPresent()) {
      ReconciliationRun r = reconOpt.get();
      boolean blocked = unresolvedMismatches > 0 || r.mismatchCount() > 0;
      reconContext = new ReconciliationContext(r.status().name(), r.completedAt() != null ? r.completedAt() : now, unresolvedMismatches, blocked, blocked);
    } else {
      reconContext = new ReconciliationContext("MATCHED", now, 0, false, false);
    }

    // 10. Research Evidence (Untrusted External Data Only)
    List<ResearchEvidence> rawEvidence = researchStore.findEvidenceByAsset(symbol.replace("/USD", "").replace("USDT", ""), 10);
    List<ResearchEvidenceContext> researchContexts = new ArrayList<>();
    for (ResearchEvidence ev : rawEvidence) {
      researchContexts.add(new ResearchEvidenceContext(
          ev.id(), ev.asset(), ev.topic(), ev.source(), ev.excerpt(),
          ev.relevanceScore(), ev.securityStatus(), ev.retrievedAt(), true
      ));
    }

    // 11. Performance
    PerformanceContext perfContext = new PerformanceContext(
        markedPositions.size(), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, summary.totalNetPnl(), summary.cumulativeFees()
    );

    // 12. Safety Summary & Gates
    List<String> blockReasons = new ArrayList<>();
    boolean mktValid = marketContext.validationStatus().equals("VALID") && marketContext.lastPrice().compareTo(BigDecimal.ZERO) > 0;
    if (!mktValid) blockReasons.add("MARKET_DATA_INVALID");

    boolean mktFresh = marketFreshnessStatus == FreshnessStatus.FRESH || marketFreshnessStatus == FreshnessStatus.AGING;
    if (!mktFresh) blockReasons.add("MARKET_DATA_STALE");

    boolean riskValid = !riskContext.isTradingBlocked() && !riskContext.isEmergencyStopped();
    if (!riskValid) blockReasons.add("RISK_STATE_BLOCKED");

    boolean reconHealthy = !reconContext.isTradingBlocked();
    if (!reconHealthy) blockReasons.add("RECONCILIATION_MISMATCH_PRESENT");

    boolean stratActive = bot.status() == BotStatus.RUNNING;
    if (!stratActive) blockReasons.add("BOT_NOT_RUNNING");

    boolean execAllowed = mktValid && mktFresh && riskValid && reconHealthy && stratActive && autonomousMode != AutonomousMode.OFF;

    SafetySummary safetySummary = new SafetySummary(
        mktValid, mktFresh, riskValid, reconHealthy, stratActive, execAllowed, blockReasons
    );

    FreshnessSummary freshnessSummary = new FreshnessSummary(
        marketFreshnessMs, 0, 0, marketFreshnessStatus
    );

    // 13. Context Hash
    UUID contextId = UUID.randomUUID();
    String contextHash = computeContextHash(botId, sessionId, symbol, marketContext.lastPrice(), summary.portfolioEquity(), now);

    TradingContext context = new TradingContext(
        contextId, contextHash, now, botId, sessionId, provider, environment, agentState, autonomousMode,
        marketContext, indicatorContext, scannerContext, strategyContext, portfolioContext,
        positionContexts, openOrderContexts, riskContext, reconContext, researchContexts,
        perfContext, freshnessSummary, safetySummary
    );

    contextStore.save(context);

    audit.record("AGENT", botId.toString(), "CONTEXT_BUILD_COMPLETED", "CONTEXT", contextId.toString(),
        Map.of("hash", contextHash, "executionAllowed", String.valueOf(execAllowed), "freshness", marketFreshnessStatus.name()));

    return context;
  }

  private String computeContextHash(UUID botId, UUID sessionId, String symbol, BigDecimal price, BigDecimal equity, Instant timestamp) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      String input = String.format("%s:%s:%s:%s:%s:%d",
          botId, sessionId, symbol,
          price != null ? price.toPlainString() : "0",
          equity != null ? equity.toPlainString() : "0",
          timestamp.getEpochSecond() / 10 // 10-second bucket for deterministic reproducibility
      );
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not supported", e);
    }
  }

  private IndicatorContext emptyIndicatorContext(String symbol, Instant time) {
    return new IndicatorContext(
        symbol, "1m", null, null, null, null, null, null, null, null, null, null, null, null,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, time
    );
  }
}
