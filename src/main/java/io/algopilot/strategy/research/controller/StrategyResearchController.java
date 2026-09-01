package io.algopilot.strategy.research.controller;

import io.algopilot.strategy.research.model.*;
import io.algopilot.strategy.research.service.StrategyExperimentService;
import io.algopilot.strategy.research.service.StrategyHealthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/research")
public class StrategyResearchController {
  private final StrategyExperimentService experimentService;
  private final StrategyHealthService healthService;

  public StrategyResearchController(
      StrategyExperimentService experimentService,
      StrategyHealthService healthService
  ) {
    this.experimentService = experimentService;
    this.healthService = healthService;
  }

  @PostMapping("/experiments")
  public ResponseEntity<StrategyExperiment> createExperiment(@RequestBody StrategyExperiment req) {
    StrategyExperiment exp = new StrategyExperiment(
        req.id() != null ? req.id() : UUID.randomUUID(),
        req.strategyId(),
        req.strategyVersionId(),
        req.symbol(),
        req.timeframe(),
        req.startDate() != null ? req.startDate() : Instant.now().minusSeconds(86400 * 30),
        req.endDate() != null ? req.endDate() : Instant.now(),
        req.initialCapital() != null ? req.initialCapital() : new BigDecimal("10000.00"),
        req.slippageModel() != null ? req.slippageModel() : SlippageModel.PERCENT,
        req.slippageBps() != null ? req.slippageBps() : new BigDecimal("5.0"),
        req.makerFeeBps() != null ? req.makerFeeBps() : new BigDecimal("10.0"),
        req.takerFeeBps() != null ? req.takerFeeBps() : new BigDecimal("20.0"),
        req.fixedSpread() != null ? req.fixedSpread() : new BigDecimal("0.50"),
        req.simulatedLatencyMs(),
        req.marketDataSource() != null ? req.marketDataSource() : "HISTORICAL_BARS",
        ExperimentStatus.CREATED,
        Instant.now()
    );
    return ResponseEntity.ok(experimentService.createExperiment(exp));
  }

  @GetMapping("/experiments")
  public ResponseEntity<List<StrategyExperiment>> getAllExperiments() {
    return ResponseEntity.ok(experimentService.getAllExperiments());
  }

  @GetMapping("/experiments/{id}")
  public ResponseEntity<StrategyExperiment> getExperiment(@PathVariable UUID id) {
    return experimentService.getExperiment(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/experiments/{id}/run")
  public ResponseEntity<ExperimentMetrics> runExperiment(@PathVariable UUID id) {
    ExperimentMetrics metrics = experimentService.runExperiment(id, null);
    return ResponseEntity.ok(metrics);
  }

  @GetMapping("/experiments/{id}/metrics")
  public ResponseEntity<ExperimentMetrics> getMetrics(@PathVariable UUID id) {
    return experimentService.getMetrics(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/experiments/{id}/trades")
  public ResponseEntity<List<ExperimentTrade>> getTrades(@PathVariable UUID id) {
    return ResponseEntity.ok(experimentService.getTrades(id));
  }

  @GetMapping("/experiments/{id}/walk-forward")
  public ResponseEntity<List<WalkForwardWindow>> getWalkForward(@PathVariable UUID id) {
    return ResponseEntity.ok(experimentService.getWalkForwardWindows(id));
  }

  @GetMapping("/experiments/{id}/sensitivity")
  public ResponseEntity<List<ParameterSensitivityResult>> getSensitivity(@PathVariable UUID id) {
    return ResponseEntity.ok(experimentService.getParameterSweeps(id));
  }

  @GetMapping("/experiments/{id}/regimes")
  public ResponseEntity<List<RegimeResult>> getRegimes(@PathVariable UUID id) {
    return ResponseEntity.ok(experimentService.getRegimes(id));
  }

  @GetMapping("/experiments/{id}/robustness")
  public ResponseEntity<Map<String, Object>> getRobustness(@PathVariable UUID id) {
    Optional<ExperimentMetrics> mOpt = experimentService.getMetrics(id);
    if (mOpt.isEmpty()) return ResponseEntity.notFound().build();
    ExperimentMetrics m = mOpt.get();

    return ResponseEntity.ok(Map.of(
        "experimentId", id,
        "robustnessScore", m.robustnessScore(),
        "netExpectancy", m.netExpectancy(),
        "profitFactor", m.profitFactor(),
        "warnings", m.warnings()
    ));
  }

  @GetMapping("/health/{strategyId}")
  public ResponseEntity<StrategyHealthMetric> getHealth(@PathVariable UUID strategyId) {
    return healthService.getLatestHealth(strategyId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
