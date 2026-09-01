package io.algopilot.strategy.discovery.controller;

import io.algopilot.strategy.discovery.model.*;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import io.algopilot.strategy.discovery.service.CandidateEvaluationService;
import io.algopilot.strategy.discovery.service.CandidateGeneratorService;
import io.algopilot.strategy.discovery.service.EconomicScenarioCalculator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strategy-discovery")
public class StrategyDiscoveryController {
  private final CandidateStore candidateStore;
  private final CandidateGeneratorService generatorService;
  private final CandidateEvaluationService evaluationService;
  private final EconomicScenarioCalculator scenarioCalculator;

  public StrategyDiscoveryController(
      CandidateStore candidateStore,
      CandidateGeneratorService generatorService,
      CandidateEvaluationService evaluationService,
      EconomicScenarioCalculator scenarioCalculator
  ) {
    this.candidateStore = candidateStore;
    this.generatorService = generatorService;
    this.evaluationService = evaluationService;
    this.scenarioCalculator = scenarioCalculator;
  }

  @PostMapping("/generate")
  public ResponseEntity<List<StrategyCandidate>> generateCandidates(
      @RequestParam(defaultValue = "BTC/USD") String symbol,
      @RequestParam(defaultValue = "1h") String timeframe,
      @RequestParam(defaultValue = "MOMENTUM") StrategyFamily family
  ) {
    List<StrategyCandidate> list = generatorService.generateGridCandidates(UUID.randomUUID(), symbol, timeframe, family);
    return ResponseEntity.ok(list);
  }

  @GetMapping("/candidates")
  public ResponseEntity<List<StrategyCandidate>> getAllCandidates() {
    return ResponseEntity.ok(candidateStore.findAllCandidates());
  }

  @GetMapping("/candidates/{id}")
  public ResponseEntity<StrategyCandidate> getCandidate(@PathVariable UUID id) {
    return candidateStore.findCandidateById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/candidates/{id}/evaluate")
  public ResponseEntity<StrategyCandidate> evaluateCandidate(@PathVariable UUID id) {
    return ResponseEntity.ok(evaluationService.evaluateCandidate(id, null));
  }

  @GetMapping("/candidates/{id}/stress")
  public ResponseEntity<List<StressResult>> getStressResults(@PathVariable UUID id) {
    return ResponseEntity.ok(candidateStore.findStressResultsByCandidateId(id));
  }

  @GetMapping("/rankings")
  public ResponseEntity<List<StrategyCandidate>> getRankings(@RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(candidateStore.findRankedCandidates(limit));
  }

  @PostMapping("/scenario")
  public ResponseEntity<ScenarioEstimate> calculateScenario(
      @RequestParam(defaultValue = "10.00") BigDecimal targetDailyProfit,
      @RequestParam(required = false) BigDecimal netExpectancy,
      @RequestParam(defaultValue = "5") int tradesPerDay
  ) {
    return ResponseEntity.ok(scenarioCalculator.calculateScenario(targetDailyProfit, netExpectancy, tradesPerDay));
  }
}
