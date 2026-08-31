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

  private final io.algopilot.bot.BotStore botStore;
  private final io.algopilot.portfolio.allocation.service.PortfolioAllocationService allocationService;

  public PortfolioRebalanceController(
      PortfolioRebalanceService service,
      PortfolioAllocationStore allocationStore,
      io.algopilot.bot.BotStore botStore,
      io.algopilot.portfolio.allocation.service.PortfolioAllocationService allocationService) {
    this.service = service;
    this.allocationStore = allocationStore;
    this.botStore = botStore;
    this.allocationService = allocationService;
  }

  @PostMapping("/evaluate-drift")
  public ResponseEntity<PortfolioDriftResult> evaluateDrift(@RequestBody(required = false) EvaluateDriftPayload payload) {
    if (payload == null) payload = new EvaluateDriftPayload(null, null, null);
    UUID planId = payload.planId();
    PortfolioAllocationPlan plan = (planId != null) 
        ? allocationStore.findPlanById(planId).orElse(null)
        : allocationStore.findLatestPlan().orElseGet(() -> allocationService.generateAllocationPlan(List.of("BTC/USD", "ETH/USD", "SOL/USD"), new BigDecimal("100000.00"), Map.of()));
    if (plan == null) {
      return ResponseEntity.notFound().build();
    }

    PortfolioDriftResult result = service.evaluateDrift(plan, payload.currentHoldingsValue(), payload.driftThresholdPct());
    return ResponseEntity.ok(result);
  }

  @PostMapping("/execute")
  public ResponseEntity<RebalanceRun> execute(@RequestBody(required = false) ExecuteRebalancePayload payload) {
    if (payload == null) payload = new ExecuteRebalancePayload(null, null, null, null);
    UUID planId = payload.planId();
    if (planId == null) {
      PortfolioAllocationPlan plan = allocationStore.findLatestPlan()
          .orElseGet(() -> allocationService.generateAllocationPlan(List.of("BTC/USD", "ETH/USD", "SOL/USD"), new BigDecimal("100000.00"), Map.of()));
      planId = plan.id();
    }

    UUID botId = payload.botId();
    if (botId == null || botStore.findById(botId).isEmpty()) {
      var allBots = botStore.findAll();
      botId = allBots.isEmpty() ? null : allBots.get(0).id();
    }

    if (botId == null) {
      throw new IllegalArgumentException("NO_ACTIVE_BOT_FOUND: Please deploy a bot first before executing portfolio rebalancing.");
    }

    RebalanceRun run = service.executeRebalance(
        planId,
        botId,
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

  @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGeneralError(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("status", "REJECTED", "reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
  }
}
