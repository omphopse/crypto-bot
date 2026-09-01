package io.algopilot.cost;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/costs")
public class AiCostController {
  private final CostStore costStore;
  private final AiCostGovernanceService costGovernance;
  private final Clock clock;

  public AiCostController(CostStore costStore, AiCostGovernanceService costGovernance, Clock clock) {
    this.costStore = costStore;
    this.costGovernance = costGovernance;
    this.clock = clock;
  }

  @GetMapping
  public ResponseEntity<List<AiCostEvent>> getRecentCosts(@RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(costStore.findRecentCostEvents(limit));
  }

  @GetMapping("/today")
  public ResponseEntity<CostSummary> getTodayCosts() {
    return ResponseEntity.ok(costStore.getCostSummaryToday(clock.instant()));
  }

  @GetMapping("/bots/{botId}")
  public ResponseEntity<CostSummary> getBotCosts(@PathVariable UUID botId) {
    return ResponseEntity.ok(costStore.getCostSummaryByBotId(botId, clock.instant()));
  }

  @GetMapping("/strategies/{strategyId}")
  public ResponseEntity<CostSummary> getStrategyCosts(@PathVariable UUID strategyId) {
    return ResponseEntity.ok(costStore.getCostSummaryByStrategyId(strategyId, clock.instant()));
  }

  @GetMapping("/budget")
  public ResponseEntity<List<AiBudgetPolicy>> getBudgetPolicies() {
    return ResponseEntity.ok(costStore.findBudgetPolicies());
  }

  @GetMapping("/budget/status")
  public ResponseEntity<Map<String, Object>> getBudgetStatus(
      @RequestParam(required = false) UUID botId,
      @RequestParam(required = false) UUID strategyId
  ) {
    BudgetStatus status = costGovernance.evaluateBudgetStatus(botId, strategyId);
    CostSummary summary = costStore.getCostSummaryToday(clock.instant());
    return ResponseEntity.ok(Map.of(
        "budgetStatus", status.name(),
        "todayTotalCostUsd", summary.grandTotalCostUsd(),
        "todayTotalRequests", summary.totalRequests(),
        "timestamp", Instant.now().toString()
    ));
  }
}
