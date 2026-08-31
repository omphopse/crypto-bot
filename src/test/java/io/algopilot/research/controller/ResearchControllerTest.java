package io.algopilot.research.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.service.ResearchAgentService;
import io.algopilot.research.service.ResearchAgentService.StrategySynthesisResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class ResearchControllerTest {
  private ResearchAgentService service;
  private ResearchController controller;

  @BeforeEach
  void setUp() {
    service = mock(ResearchAgentService.class);
    controller = new ResearchController(service);
  }

  @Test
  void testEvaluateFactors() {
    UUID hypId = UUID.randomUUID();
    AlphaHypothesis hypothesis = new AlphaHypothesis(
        hypId, "BTC/USD", "1h", new BigDecimal("0.4500"), Collections.emptyList(), "Bullish momentum", Instant.now()
    );
    when(service.evaluateFactors(anyString(), anyString(), anyList())).thenReturn(hypothesis);

    ResponseEntity<AlphaHypothesis> response = controller.evaluateFactors(new ResearchController.EvaluateFactorsPayload("BTC/USD", "1h", Collections.emptyList()));
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(hypId, response.getBody().id());
  }

  @Test
  void testSynthesizeStrategy() {
    UUID candId = UUID.randomUUID();
    Instant now = Instant.now();
    AlphaHypothesis hypothesis = new AlphaHypothesis(UUID.randomUUID(), "BTC/USD", "1h", BigDecimal.ZERO, Collections.emptyList(), "", now);
    BacktestResult backtest = new BacktestResult(UUID.randomUUID(), UUID.randomUUID(), "BTC/USD", "1h", now, now, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, Collections.emptyList(), Collections.emptyList(), now);
    WalkForwardResult wf = new WalkForwardResult(UUID.randomUUID(), UUID.randomUUID(), "BTC/USD", "1h", 3, BigDecimal.ONE, Collections.emptyList(), now);

    StrategySynthesisResult synthRes = new StrategySynthesisResult(
        candId, UUID.randomUUID(), UUID.randomUUID(), "Alpha-BTC", hypothesis, backtest, wf, true, "APPROVED_CANDIDATE"
    );
    when(service.synthesizeStrategy(any(), anyList())).thenReturn(synthRes);

    ResponseEntity<StrategySynthesisResult> response = controller.synthesizeStrategy(
        new ResearchController.SynthesizeStrategyPayload("BTC/USD", "1h", BigDecimal.ONE, BigDecimal.valueOf(10), Collections.emptyList())
    );
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(candId, response.getBody().candidateId());
    assertEquals("APPROVED_CANDIDATE", response.getBody().status());
  }
}
