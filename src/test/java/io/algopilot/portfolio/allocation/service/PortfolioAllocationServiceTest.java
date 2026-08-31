package io.algopilot.portfolio.allocation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.model.Candle;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.persistence.PortfolioAllocationStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PortfolioAllocationServiceTest {
  private PortfolioAllocationStore store;
  private AuditEventWriter audit;
  private PortfolioAllocationService service;

  @BeforeEach
  void setUp() {
    store = mock(PortfolioAllocationStore.class);
    audit = mock(AuditEventWriter.class);
    service = new PortfolioAllocationService(store, audit);
  }

  @Test
  void testGenerateAllocationPlan_persistsAndAudits() {
    Instant now = Instant.now();
    List<Candle> candlesA = List.of(
        new Candle("BTC/USD", "1h", new BigDecimal("50000"), new BigDecimal("50000"), new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ONE, now),
        new Candle("BTC/USD", "1h", new BigDecimal("51000"), new BigDecimal("51000"), new BigDecimal("51000"), new BigDecimal("51000"), BigDecimal.ONE, now.plusSeconds(3600))
    );
    List<Candle> candlesB = List.of(
        new Candle("ETH/USD", "1h", new BigDecimal("3000"), new BigDecimal("3000"), new BigDecimal("3000"), new BigDecimal("3000"), BigDecimal.ONE, now),
        new Candle("ETH/USD", "1h", new BigDecimal("3100"), new BigDecimal("3100"), new BigDecimal("3100"), new BigDecimal("3100"), BigDecimal.ONE, now.plusSeconds(3600))
    );

    Map<String, List<Candle>> data = Map.of("BTC/USD", candlesA, "ETH/USD", candlesB);
    PortfolioAllocationPlan plan = service.generateAllocationPlan(List.of("BTC/USD", "ETH/USD"), new BigDecimal("100000.00"), data);

    assertNotNull(plan);
    assertEquals(2, plan.targetWeights().size());
    assertNotNull(plan.portfolioVolatility());
    assertNotNull(plan.valueAtRisk95());
    assertNotNull(plan.expectedShortfall95());

    verify(store).savePlan(plan);
    verify(audit).record(eq("PORTFOLIO_ALLOCATION"), eq("SYSTEM"), eq("PORTFOLIO_ALLOCATION_GENERATED"), eq("ALLOCATION_PLAN"), eq(plan.id().toString()), any());
  }

  @Test
  void testGenerateAllocationPlan_rejectsEmptySymbols() {
    assertThrows(IllegalArgumentException.class, () -> service.generateAllocationPlan(Collections.emptyList(), BigDecimal.ONE, Collections.emptyMap()));
  }
}
