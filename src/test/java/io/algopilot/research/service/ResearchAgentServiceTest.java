package io.algopilot.research.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.service.BacktestService;
import io.algopilot.research.factor.FactorEngine;
import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.persistence.ResearchStore;
import io.algopilot.strategy.StrategyService;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ResearchAgentServiceTest {
  private FactorEngine factorEngine;
  private ResearchStore researchStore;
  private StrategyService strategyService;
  private BacktestService backtestService;
  private AuditEventWriter audit;
  private ObjectMapper json;
  private ResearchAgentService service;

  @BeforeEach
  void setUp() {
    factorEngine = new FactorEngine();
    researchStore = mock(ResearchStore.class);
    strategyService = mock(StrategyService.class);
    backtestService = mock(BacktestService.class);
    audit = mock(AuditEventWriter.class);
    json = new ObjectMapper();

    service = new ResearchAgentService(factorEngine, researchStore, strategyService, backtestService, audit, json);
  }

  @Test
  void testEvaluateFactors_createsAndPersistsHypothesis() {
    AlphaHypothesis hypothesis = service.evaluateFactors("BTC/USD", "1h", Collections.emptyList());

    assertNotNull(hypothesis);
    assertEquals("BTC/USD", hypothesis.symbol());
    assertEquals("1h", hypothesis.timeframe());
    verify(researchStore).saveHypothesis(hypothesis);
    verify(audit).record(eq("RESEARCH_AGENT"), eq("RESEARCH_SYSTEM"), eq("RESEARCH_HYPOTHESIS_GENERATED"), eq("ALPHA_HYPOTHESIS"), eq(hypothesis.id().toString()), any());
  }

  @Test
  void testSynthesizeStrategy_validatesAndGatesCandidate() {
    UUID stratId = UUID.randomUUID();
    UUID stratVersionId = UUID.randomUUID();
    UUID backtestId = UUID.randomUUID();
    UUID wfId = UUID.randomUUID();
    Instant now = Instant.now();

    StrategyVersion version = new StrategyVersion(stratVersionId, stratId, 1, json.createObjectNode(), "Test", now);
    when(strategyService.create(any())).thenReturn(version);

    BacktestResult backtestRes = new BacktestResult(
        backtestId, stratVersionId, "BTC/USD", "1h", now, now,
        new BigDecimal("100000.00"), new BigDecimal("115000.00"), new BigDecimal("15.0000"),
        10, 7, 3, new BigDecimal("70.0000"), new BigDecimal("4.5000"), new BigDecimal("2.1000"),
        new BigDecimal("2.8000"), Collections.emptyList(), Collections.emptyList(), now
    );
    when(backtestService.runBacktest(any(), anyList())).thenReturn(backtestRes);

    WalkForwardResult wfRes = new WalkForwardResult(
        wfId, stratVersionId, "BTC/USD", "1h", 3, new BigDecimal("0.8500"), Collections.emptyList(), now
    );
    when(backtestService.runWalkForward(any(), anyList())).thenReturn(wfRes);

    var req = new ResearchAgentService.StrategySynthesisRequest("BTC/USD", "1h", new BigDecimal("1.50"), new BigDecimal("10.00"));
    var result = service.synthesizeStrategy(req, List.of(new Candle("BTC/USD", "1h", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, now)));

    assertNotNull(result);
    assertEquals("APPROVED_CANDIDATE", result.status());
    assertTrue(result.meetsDeploymentCriteria());
    verify(researchStore).saveCandidate(any());
    verify(audit).record(eq("RESEARCH_AGENT"), eq("RESEARCH_SYSTEM"), eq("STRATEGY_SYNTHESIS_EVALUATED"), eq("STRATEGY_CANDIDATE"), eq(result.candidateId().toString()), any());
  }
}
