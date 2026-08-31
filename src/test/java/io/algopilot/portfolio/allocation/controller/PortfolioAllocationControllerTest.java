package io.algopilot.portfolio.allocation.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.service.PortfolioAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class PortfolioAllocationControllerTest {
  private PortfolioAllocationService service;
  private PortfolioAllocationController controller;

  @BeforeEach
  void setUp() {
    service = mock(PortfolioAllocationService.class);
    controller = new PortfolioAllocationController(service);
  }

  @Test
  void testAllocate_returnsPlan() {
    UUID planId = UUID.randomUUID();
    PortfolioAllocationPlan plan = new PortfolioAllocationPlan(
        planId, new BigDecimal("100000.00"), new BigDecimal("0.0150"),
        new BigDecimal("2500.00"), new BigDecimal("3200.00"), Collections.emptyList(), "Test plan", Instant.now()
    );
    when(service.generateAllocationPlan(anyList(), any(), any())).thenReturn(plan);

    ResponseEntity<PortfolioAllocationPlan> response = controller.allocate(
        new PortfolioAllocationController.AllocatePayload(null, new BigDecimal("100000.00"), Collections.emptyMap())
    );

    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(planId, response.getBody().id());
  }

  @Test
  void testGetLatest_notFoundReturns404() {
    when(service.getLatestPlan()).thenReturn(Optional.empty());
    ResponseEntity<PortfolioAllocationPlan> response = controller.getLatest();
    assertEquals(404, response.getStatusCode().value());
  }
}
