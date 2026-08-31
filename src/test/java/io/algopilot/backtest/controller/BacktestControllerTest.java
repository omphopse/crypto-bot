package io.algopilot.backtest.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.service.BacktestService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class BacktestControllerTest {
  private BacktestService service;
  private BacktestController controller;

  @BeforeEach
  void setUp() {
    service = mock(BacktestService.class);
    controller = new BacktestController(service);
  }

  @Test
  void testRunBacktest_returnsResult() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    BacktestResult backtest = new BacktestResult(
        id, UUID.randomUUID(), "BTC/USD", "1h", now, now,
        new BigDecimal("100000"), new BigDecimal("105000"), new BigDecimal("5.0"),
        2, 1, 1, new BigDecimal("50.0"), new BigDecimal("2.0"), new BigDecimal("1.5"),
        new BigDecimal("2.0"), Collections.emptyList(), Collections.emptyList(), now
    );
    when(service.runBacktest(any(), anyList())).thenReturn(backtest);

    BacktestController.BacktestPayload payload = new BacktestController.BacktestPayload(
        UUID.randomUUID(), "BTC/USD", "1h", now, now, new BigDecimal("100000"), 5, 10, Collections.emptyList()
    );

    ResponseEntity<BacktestResult> response = controller.runBacktest(payload);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(id, response.getBody().id());
  }

  @Test
  void testGetBacktest_notFoundReturns404() {
    UUID id = UUID.randomUUID();
    when(service.getBacktest(id)).thenReturn(Optional.empty());

    ResponseEntity<BacktestResult> response = controller.getBacktest(id);
    assertEquals(404, response.getStatusCode().value());
  }
}
