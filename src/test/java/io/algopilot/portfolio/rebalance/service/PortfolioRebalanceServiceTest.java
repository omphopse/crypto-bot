package io.algopilot.portfolio.rebalance.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.adapter.ExecutionGateway;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderService;
import io.algopilot.order.OrderStatus;
import io.algopilot.portfolio.allocation.model.AllocationWeight;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import io.algopilot.portfolio.rebalance.model.PortfolioDriftResult;
import io.algopilot.portfolio.rebalance.model.RebalanceRun;
import io.algopilot.portfolio.rebalance.persistence.RebalanceStore;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PortfolioRebalanceServiceTest {
  private PortfolioAllocationStore allocationStore;
  private RebalanceStore rebalanceStore;
  private OrderService orderService;
  private ExecutionGateway executionGateway;
  private AuditEventWriter audit;
  private io.algopilot.bot.BotStore botStore;
  private PortfolioRebalanceService service;

  @BeforeEach
  void setUp() {
    allocationStore = mock(PortfolioAllocationStore.class);
    rebalanceStore = mock(RebalanceStore.class);
    orderService = mock(OrderService.class);
    executionGateway = mock(ExecutionGateway.class);
    audit = mock(AuditEventWriter.class);
    botStore = mock(io.algopilot.bot.BotStore.class);

    service = new PortfolioRebalanceService(
        allocationStore, rebalanceStore, orderService, executionGateway, audit, botStore
    );
  }

  @Test
  void testEvaluateDrift_detectsRequiredRebalancing() {
    UUID planId = UUID.randomUUID();
    List<AllocationWeight> weights = List.of(
        new AllocationWeight("BTC/USD", new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO),
        new AllocationWeight("ETH/USD", new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO)
    );
    PortfolioAllocationPlan plan = new PortfolioAllocationPlan(
        planId, new BigDecimal("100000.00"), new BigDecimal("0.02"),
        new BigDecimal("2000"), new BigDecimal("2600"), weights, "Test", Instant.now()
    );

    // Current holdings: BTC is 80k (80%), ETH is 20k (20%). Drift is 30% on each, exceeding 5% threshold.
    Map<String, BigDecimal> holdings = Map.of(
        "BTC/USD", new BigDecimal("80000.00"),
        "ETH/USD", new BigDecimal("20000.00")
    );

    PortfolioDriftResult result = service.evaluateDrift(plan, holdings, new BigDecimal("5.00"));

    assertNotNull(result);
    assertTrue(result.rebalanceRequired());
    assertEquals(new BigDecimal("30.0000"), result.maxDriftPct());
    assertEquals(2, result.proposedOrders().size());
  }

  @Test
  void testExecuteRebalance_evaluatesRiskAndDispatches() {
    UUID planId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Instant now = Instant.now();

    List<AllocationWeight> weights = List.of(
        new AllocationWeight("BTC/USD", new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO),
        new AllocationWeight("ETH/USD", new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO)
    );
    PortfolioAllocationPlan plan = new PortfolioAllocationPlan(
        planId, new BigDecimal("100000.00"), new BigDecimal("0.02"),
        new BigDecimal("2000"), new BigDecimal("2600"), weights, "Test", now
    );
    when(allocationStore.findPlanById(planId)).thenReturn(Optional.of(plan));

    OrderRecord createdOrder = new OrderRecord(
        orderId, "client-123", botId.toString(), UUID.randomUUID().toString(), "BTC/USD",
        RiskDecisionRequest.Side.BUY, BigDecimal.ONE, new BigDecimal("60000"), OrderStatus.CREATED, now
    );
    when(orderService.create(any())).thenReturn(createdOrder);

    Map<String, BigDecimal> holdings = Map.of(
        "BTC/USD", new BigDecimal("80000.00"),
        "ETH/USD", new BigDecimal("20000.00")
    );

    RebalanceRun run = service.executeRebalance(planId, botId, holdings, new BigDecimal("5.00"));

    assertNotNull(run);
    assertEquals("COMPLETED", run.status());
    assertEquals(2, run.executedCount());
    assertEquals(0, run.failedCount());

    verify(orderService, times(2)).create(any());
    verify(executionGateway, times(2)).dispatch(eq(orderId));
    verify(rebalanceStore).saveRun(any());
    verify(rebalanceStore).updateRun(any());
    verify(audit, times(2)).record(eq("PORTFOLIO_REBALANCE"), eq("SYSTEM"), anyString(), eq("REBALANCE_RUN"), eq(run.id().toString()), any());
  }
}
