package io.algopilot.backtest.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.engine.BacktestEngine;
import io.algopilot.backtest.engine.WalkForwardEngine;
import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.persistence.BacktestStore;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BacktestServiceTest {
  private BacktestEngine backtestEngine;
  private WalkForwardEngine walkForwardEngine;
  private BacktestStore backtestStore;
  private StrategyStore strategyStore;
  private AuditEventWriter audit;
  private BacktestService service;

  @BeforeEach
  void setUp() {
    backtestEngine = mock(BacktestEngine.class);
    walkForwardEngine = mock(WalkForwardEngine.class);
    backtestStore = mock(BacktestStore.class);
    strategyStore = mock(StrategyStore.class);
    audit = mock(AuditEventWriter.class);

    service = new BacktestService(backtestEngine, walkForwardEngine, backtestStore, strategyStore, audit);
  }

  @Test
  void testRunBacktest_persistsAndAudits() {
    UUID stratVersionId = UUID.randomUUID();
    UUID backtestId = UUID.randomUUID();
    Instant now = Instant.now();

    StrategyVersion version = new StrategyVersion(stratVersionId, UUID.randomUUID(), 1, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), "Initial", now);
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.of(version));

    BacktestRequest request = new BacktestRequest(
        stratVersionId, "BTC/USD", "1h", now, now, new BigDecimal("100000.00"), 5, 10
    );
    BacktestResult engineResult = new BacktestResult(
        backtestId, stratVersionId, "BTC/USD", "1h", now, now,
        new BigDecimal("100000.00"), new BigDecimal("105000.00"), new BigDecimal("5.0000"),
        5, 3, 2, new BigDecimal("60.0000"), new BigDecimal("2.5000"), new BigDecimal("1.8000"),
        new BigDecimal("2.1000"), Collections.emptyList(), Collections.emptyList(), now
    );
    when(backtestEngine.run(eq(request), anyList())).thenReturn(engineResult);

    BacktestResult result = service.runBacktest(request, List.of(new Candle("BTC/USD", "1h", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, now)));

    assertNotNull(result);
    assertEquals(backtestId, result.id());
    verify(backtestStore).saveBacktest(engineResult);
    verify(audit).record(eq("BACKTEST"), eq(stratVersionId.toString()), eq("BACKTEST_EXECUTED"), eq("BACKTEST"), eq(backtestId.toString()), any());
  }

  @Test
  void testRunBacktest_rejectsIfStrategyVersionNotFound() {
    UUID stratVersionId = UUID.randomUUID();
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.empty());

    BacktestRequest request = new BacktestRequest(
        stratVersionId, "BTC/USD", "1h", Instant.now(), Instant.now(), new BigDecimal("100000.00"), 5, 10
    );

    assertThrows(IllegalArgumentException.class, () -> service.runBacktest(request, Collections.emptyList()));
  }
}
