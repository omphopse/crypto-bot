package io.algopilot.portfolio.rebalance.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import io.algopilot.portfolio.rebalance.model.PortfolioDriftResult;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import io.algopilot.portfolio.rebalance.service.PortfolioRebalanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class PortfolioRebalanceControllerTest {
  private PortfolioRebalanceService service;
  private PortfolioAllocationStore allocationStore;
  private io.algopilot.bot.BotStore botStore;
  private io.algopilot.portfolio.allocation.service.PortfolioAllocationService allocationService;
  private PortfolioRebalanceController controller;

  @BeforeEach
  void setUp() {
    service = mock(PortfolioRebalanceService.class);
    allocationStore = mock(PortfolioAllocationStore.class);
    botStore = mock(io.algopilot.bot.BotStore.class);
    allocationService = mock(io.algopilot.portfolio.allocation.service.PortfolioAllocationService.class);
    controller = new PortfolioRebalanceController(service, allocationStore, botStore, allocationService);
  }

  @Test
  void testEvaluateDrift_returnsDriftResult() {
    UUID planId = UUID.randomUUID();
    PortfolioAllocationPlan plan = new PortfolioAllocationPlan(
        planId, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, Collections.emptyList(), "Test", Instant.now()
    );
    when(allocationStore.findPlanById(planId)).thenReturn(Optional.of(plan));

    PortfolioDriftResult drift = new PortfolioDriftResult(
        planId, false, BigDecimal.ZERO, Collections.emptyMap(), Collections.emptyList(), Instant.now()
    );
    when(service.evaluateDrift(any(), any(), any())).thenReturn(drift);

    ResponseEntity<PortfolioDriftResult> response = controller.evaluateDrift(
        new PortfolioRebalanceController.EvaluateDriftPayload(planId, Collections.emptyMap(), BigDecimal.valueOf(5))
    );

    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(planId, response.getBody().planId());
  }

  @Test
  void testExecute_returnsRebalanceRun() {
    UUID planId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    Instant now = Instant.now();

    RebalanceRun run = new RebalanceRun(
        runId, planId, "COMPLETED", new BigDecimal("12.5"), 2, 2, 0, "Test", now, now
    );
    when(service.executeRebalance(any(), any(), any(), any())).thenReturn(run);

    ResponseEntity<RebalanceRun> response = controller.execute(
        new PortfolioRebalanceController.ExecuteRebalancePayload(planId, botId, Collections.emptyMap(), BigDecimal.valueOf(5))
    );

    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(runId, response.getBody().id());
  }
}
