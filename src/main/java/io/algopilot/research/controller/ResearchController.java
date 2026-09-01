package io.algopilot.research.controller;

import io.algopilot.backtest.model.Candle;
import io.algopilot.research.model.AlphaHypothesis;
import io.algopilot.research.model.StrategyCandidate;
import io.algopilot.research.service.ResearchAgentService;
import io.algopilot.research.service.ResearchAgentService.StrategySynthesisRequest;
import io.algopilot.research.service.ResearchAgentService.StrategySynthesisResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("alphaHypothesisResearchController")
@RequestMapping("/api/research")
public class ResearchController {
  private final ResearchAgentService researchService;

  public record EvaluateFactorsPayload(
      String symbol,
      String timeframe,
      List<Candle> candleData
  ) {}

  public record SynthesizeStrategyPayload(
      String symbol,
      String timeframe,
      BigDecimal targetSharpe,
      BigDecimal maxAcceptableDrawdownPct,
      List<Candle> candleData
  ) {}

  public ResearchController(ResearchAgentService researchService) {
    this.researchService = researchService;
  }

  @PostMapping({"/evaluate-factors", "/factors/evaluate"})
  public ResponseEntity<AlphaHypothesis> evaluateFactors(@RequestBody(required = false) EvaluateFactorsPayload payload) {
    String sym = (payload != null && payload.symbol() != null) ? payload.symbol() : "BTC/USD";
    String tf = (payload != null && payload.timeframe() != null) ? payload.timeframe() : "1h";
    List<Candle> candles = (payload != null && payload.candleData() != null && !payload.candleData().isEmpty())
        ? payload.candleData()
        : generateSyntheticCandles(sym, tf, 100);

    AlphaHypothesis hypothesis = researchService.evaluateFactors(sym, tf, candles);
    return ResponseEntity.ok(hypothesis);
  }

  @PostMapping({"/synthesize-strategy", "/synthesize"})
  public ResponseEntity<StrategySynthesisResult> synthesizeStrategy(@RequestBody(required = false) SynthesizeStrategyPayload payload) {
    if (payload == null) payload = new SynthesizeStrategyPayload(null, null, null, null, null);
    String symbol = payload.symbol() != null ? payload.symbol() : "BTC/USD";
    String timeframe = payload.timeframe() != null ? payload.timeframe() : "1h";
    List<Candle> candles = payload.candleData() != null && !payload.candleData().isEmpty()
        ? payload.candleData()
        : generateSyntheticCandles(symbol, timeframe, 200);

    StrategySynthesisRequest req = new StrategySynthesisRequest(
        symbol,
        timeframe,
        payload.targetSharpe() != null ? payload.targetSharpe() : new BigDecimal("0.50"),
        payload.maxAcceptableDrawdownPct() != null ? payload.maxAcceptableDrawdownPct() : new BigDecimal("20.00")
    );

    StrategySynthesisResult result = researchService.synthesizeStrategy(req, candles);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/hypotheses")
  public List<AlphaHypothesis> listHypotheses(@RequestParam(defaultValue = "10") int limit) {
    return researchService.getRecentHypotheses(limit);
  }

  @GetMapping("/candidates")
  public List<StrategyCandidate> listCandidates(@RequestParam(defaultValue = "10") int limit) {
    return researchService.getRecentCandidates(limit);
  }

  private List<Candle> generateSyntheticCandles(String symbol, String timeframe, int count) {
    List<Candle> list = new ArrayList<>(count);
    BigDecimal current = new BigDecimal("60000.00");
    Instant now = Instant.now().minus(count, ChronoUnit.HOURS);

    for (int i = 0; i < count; i++) {
      double pctChange = Math.sin(i / 8.0) * 0.015 + 0.003;
      BigDecimal next = current.multiply(BigDecimal.valueOf(1.0 + pctChange));
      BigDecimal high = current.max(next).multiply(BigDecimal.valueOf(1.002));
      BigDecimal low = current.min(next).multiply(BigDecimal.valueOf(0.998));
      BigDecimal vol = BigDecimal.valueOf(15.0 + Math.abs(pctChange) * 120);

      list.add(new Candle(symbol, timeframe, current, high, low, next, vol, now.plus(i, ChronoUnit.HOURS)));
      current = next;
    }
    return list;
  }
}
