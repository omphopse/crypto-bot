package io.algopilot.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardRequest;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.service.BacktestService;
import io.algopilot.research.factor.FactorEngine;
import io.algopilot.research.factor.FactorScore;
import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.model.StrategyCandidate;
import io.algopilot.research.persistence.ResearchStore;
import io.algopilot.strategy.CreateStrategyRequest;
import io.algopilot.strategy.StrategyService;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchAgentService {
  private static final Logger log = LoggerFactory.getLogger(ResearchAgentService.class);

  private final FactorEngine factorEngine;
  private final ResearchStore researchStore;
  private final StrategyService strategyService;
  private final BacktestService backtestService;
  private final AuditEventWriter audit;
  private final ObjectMapper json;
  private final Clock clock;

  public record StrategySynthesisRequest(
      String symbol,
      String timeframe,
      BigDecimal targetSharpe,
      BigDecimal maxAcceptableDrawdownPct
  ) {}

  public record StrategySynthesisResult(
      UUID candidateId,
      UUID strategyId,
      UUID strategyVersionId,
      String name,
      AlphaHypothesis hypothesis,
      BacktestResult backtestResult,
      WalkForwardResult walkForwardResult,
      boolean meetsDeploymentCriteria,
      String status
  ) {}

  @org.springframework.beans.factory.annotation.Autowired
  public ResearchAgentService(
      FactorEngine factorEngine,
      ResearchStore researchStore,
      StrategyService strategyService,
      BacktestService backtestService,
      AuditEventWriter audit,
      ObjectMapper json) {
    this(factorEngine, researchStore, strategyService, backtestService, audit, json, Clock.systemUTC());
  }

  public ResearchAgentService(
      FactorEngine factorEngine,
      ResearchStore researchStore,
      StrategyService strategyService,
      BacktestService backtestService,
      AuditEventWriter audit,
      ObjectMapper json,
      Clock clock) {
    this.factorEngine = factorEngine;
    this.researchStore = researchStore;
    this.strategyService = strategyService;
    this.backtestService = backtestService;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public AlphaHypothesis evaluateFactors(String symbol, String timeframe, List<Candle> candleData) {
    var eval = factorEngine.evaluate(candleData);
    UUID hypothesisId = UUID.randomUUID();

    StringBuilder rationale = new StringBuilder();
    rationale.append("Alpha composite score: ").append(eval.compositeScore().toPlainString()).append(". ");
    for (FactorScore f : eval.factors()) {
      rationale.append("[").append(f.factorName()).append(": ").append(f.normalizedScore().toPlainString()).append(" - ").append(f.explanation()).append("] ");
    }

    AlphaHypothesis hypothesis = new AlphaHypothesis(
        hypothesisId,
        symbol != null ? symbol : "BTC/USD",
        timeframe != null ? timeframe : "1h",
        eval.compositeScore(),
        eval.factors(),
        rationale.toString().trim(),
        clock.instant()
    );

    researchStore.saveHypothesis(hypothesis);

    audit.record(
        "RESEARCH_AGENT",
        "RESEARCH_SYSTEM",
        "RESEARCH_HYPOTHESIS_GENERATED",
        "ALPHA_HYPOTHESIS",
        hypothesis.id().toString(),
        Map.of(
            "symbol", hypothesis.symbol(),
            "timeframe", hypothesis.timeframe(),
            "compositeScore", hypothesis.compositeScore().toPlainString(),
            "factorsCount", hypothesis.factors().size()
        )
    );

    return hypothesis;
  }

  @Transactional
  public StrategySynthesisResult synthesizeStrategy(StrategySynthesisRequest req, List<Candle> candleData) {
    String symbol = req.symbol() != null ? req.symbol() : "BTC/USD";
    String timeframe = req.timeframe() != null ? req.timeframe() : "1h";
    BigDecimal targetSharpe = req.targetSharpe() != null ? req.targetSharpe() : BigDecimal.valueOf(1.00);
    BigDecimal maxDrawdown = req.maxAcceptableDrawdownPct() != null ? req.maxAcceptableDrawdownPct() : BigDecimal.valueOf(15.00);

    // 1. Evaluate factor matrix
    AlphaHypothesis hypothesis = evaluateFactors(symbol, timeframe, candleData);

    // 2. Synthesize Strategy Definition
    ObjectNode definition = json.createObjectNode();
    definition.put("symbol", symbol);
    definition.put("timeframe", timeframe);
    definition.put("compositeAlphaScore", hypothesis.compositeScore().doubleValue());
    definition.put("fastEmaPeriod", 9);
    definition.put("slowEmaPeriod", 21);
    definition.put("rsiPeriod", 14);
    definition.put("rsiOverbought", 70);
    definition.put("rsiOversold", 30);
    definition.put("stopLossPct", 2.5);
    definition.put("takeProfitPct", 5.0);

    String strategyName = "AlphaFactor-" + symbol.replace("/", "") + "-" + timeframe;
    StrategyVersion version = strategyService.create(new CreateStrategyRequest(
        strategyName,
        definition,
        "Synthesized by Research Agent from AlphaHypothesis " + hypothesis.id()
    ));

    // 3. Deterministic Backtest Validation
    Instant start = candleData != null && !candleData.isEmpty() ? candleData.get(0).timestamp() : clock.instant();
    Instant end = candleData != null && !candleData.isEmpty() ? candleData.get(candleData.size() - 1).timestamp() : clock.instant();

    BacktestRequest backtestReq = new BacktestRequest(
        version.id(), symbol, timeframe, start, end, new BigDecimal("100000.00"), 5, 10
    );
    BacktestResult backtestResult = backtestService.runBacktest(backtestReq, candleData);

    // 4. Walk-Forward Validation
    WalkForwardRequest wfReq = new WalkForwardRequest(
        version.id(), symbol, timeframe, 3, 30, 15, new BigDecimal("100000.00")
    );
    WalkForwardResult wfResult = backtestService.runWalkForward(wfReq, candleData);

    // 5. Candidate Qualification Gating
    boolean meetsSharpe = backtestResult.sharpeRatio().compareTo(targetSharpe) >= 0;
    boolean meetsDrawdown = backtestResult.maxDrawdownPct().compareTo(maxDrawdown) <= 0;
    boolean meetsWfe = wfResult.avgOosEfficiency().signum() > 0;
    boolean qualifies = meetsSharpe && meetsDrawdown && meetsWfe;

    String status = qualifies ? "APPROVED_CANDIDATE" : "REJECTED_CANDIDATE";
    UUID candidateId = UUID.randomUUID();

    StrategyCandidate candidate = new StrategyCandidate(
        candidateId,
        hypothesis.id(),
        version.id(),
        backtestResult.id(),
        wfResult.id(),
        status,
        clock.instant()
    );

    researchStore.saveCandidate(candidate);

    audit.record(
        "RESEARCH_AGENT",
        "RESEARCH_SYSTEM",
        "STRATEGY_SYNTHESIS_EVALUATED",
        "STRATEGY_CANDIDATE",
        candidate.id().toString(),
        Map.of(
            "strategyVersionId", version.id().toString(),
            "status", status,
            "sharpeRatio", backtestResult.sharpeRatio().toPlainString(),
            "maxDrawdownPct", backtestResult.maxDrawdownPct().toPlainString(),
            "avgOosEfficiency", wfResult.avgOosEfficiency().toPlainString(),
            "qualifies", qualifies
        )
    );

    return new StrategySynthesisResult(
        candidateId,
        version.strategyId(),
        version.id(),
        strategyName,
        hypothesis,
        backtestResult,
        wfResult,
        qualifies,
        status
    );
  }

  public List<AlphaHypothesis> getRecentHypotheses(int limit) {
    return researchStore.findRecentHypotheses(limit);
  }

  public List<StrategyCandidate> getRecentCandidates(int limit) {
    return researchStore.findRecentCandidates(limit);
  }
}
