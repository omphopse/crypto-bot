package io.algopilot.portfolio.rebalance.controller;

import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import io.algopilot.portfolio.rebalance.model.PortfolioDriftResult;
import io.algopilot.portfolio.rebalance.model.RebalanceOrder;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import io.algopilot.portfolio.rebalance.service.PortfolioRebalanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/rebalance")
public class PortfolioRebalanceController {
  private final PortfolioRebalanceService service;
  private final PortfolioAllocationStore allocationStore;

  public record EvaluateDriftPayload(
      UUID planId,
      Map<String, BigDecimal> currentHoldingsValue,
      BigDecimal driftThresholdPct
  ) {}

  public record ExecuteRebalancePayload(
      UUID planId,
      UUID botId,
      Map<String, BigDecimal> currentHoldingsValue,
      BigDecimal driftThresholdPct
  ) {}

  public PortfolioRebalanceController(PortfolioRebalanceService service, PortfolioAllocationStore allocationStore) {
    this.service = service;
    this.allocationStore = allocationStore;
  }

  @PostMapping("/evaluate-drift")
  public ResponseEntity<PortfolioDriftResult> evaluateDrift(@RequestBody EvaluateDriftPayload payload) {
    if (payload.planId() == null) {
      return ResponseEntity.badRequest().build();
    }
    PortfolioAllocationPlan plan = allocationStore.findPlanById(payload.planId())
        .orElse(null);
    if (plan == null) {
      return ResponseEntity.notFound().build();
    }

    PortfolioDriftResult result = service.evaluateDrift(plan, payload.currentHoldingsValue(), payload.driftThresholdPct());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/execute")
  public ResponseEntity<RebalanceRun> execute(@RequestBody ExecuteRebalancePayload payload) {
    if (payload.planId() == null || payload.botId() == null) {
      return ResponseEntity.badRequest().build();
    }

    RebalanceRun run = service.executeRebalance(
        payload.planId(),
        payload.botId(),
        payload.currentHoldingsValue(),
        payload.driftThresholdPct()
    );
    return ResponseEntity.ok(run);
  }

  @GetMapping("/runs")
  public List<RebalanceRun> listRuns(@RequestParam(defaultValue = "10") int limit) {
    return service.getRecentRuns(limit);
  }

  @GetMapping("/runs/{id}")
  public ResponseEntity<RebalanceRun> getRun(@PathVariable UUID id) {
    return service.getRun(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/runs/{id}/orders")
  public List<RebalanceOrder> getRunOrders(@PathVariable UUID id) {
    return service.getRunOrders(id);
  }
}
