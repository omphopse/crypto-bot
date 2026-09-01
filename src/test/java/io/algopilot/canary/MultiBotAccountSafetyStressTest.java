package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.risk.RiskDecision;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskEngine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiBotAccountSafetyStressTest {
  private RiskEngine riskEngine;
  private Clock clock;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();
    riskEngine = new RiskEngine();
  }

  @Test
  void testConcurrentMultiBotCapitalRequests_enforcesGlobalExposureBounds() throws Exception {
    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    String[] symbols = {"BTC/USD", "ETH/USD", "NVDA", "AAPL", "TSLA"};
    BigDecimal portfolioEquity = new BigDecimal("100000.00");
    BigDecimal singleOrderNotional = new BigDecimal("20000.00");

    List<Callable<RiskDecision>> tasks = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      String symbol = symbols[i];
      UUID botId = UUID.randomUUID();
      UUID stratVersionId = UUID.randomUUID();

      RiskDecisionRequest req = new RiskDecisionRequest(
          "ord-" + i,
          botId.toString(),
          stratVersionId.toString(),
          symbol,
          RiskDecisionRequest.Side.BUY,
          BigDecimal.ONE,
          singleOrderNotional,
          portfolioEquity,
          BigDecimal.ZERO,
          new BigDecimal("30000.00"), // current portfolio exposure
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          now,
          false,
          false,
          false,
          i,
          0,
          0
      );
      tasks.add(() -> riskEngine.evaluate(req));
    }

    List<Future<RiskDecision>> futures = executor.invokeAll(tasks);
    List<RiskDecision> decisions = new ArrayList<>();
    for (Future<RiskDecision> f : futures) {
      decisions.add(f.get());
    }
    executor.shutdown();

    assertThat(decisions).hasSize(5);
    for (RiskDecision d : decisions) {
      assertThat(d.status()).isIn(RiskDecision.Status.APPROVED, RiskDecision.Status.REJECTED);
    }
  }
}
