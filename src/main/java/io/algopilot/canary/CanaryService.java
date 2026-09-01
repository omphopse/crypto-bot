package io.algopilot.canary;

import io.algopilot.agent.execution.AutonomousExecutionOrchestrator;
import io.algopilot.agent.execution.AutonomousExecutionResult;
import io.algopilot.agent.runtime.AutonomousBotRunner;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.cost.CostStore;
import io.algopilot.cost.CostSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CanaryService {
  private static final Logger log = LoggerFactory.getLogger(CanaryService.class);

  private final BotStore botStore;
  private final AutonomousBotRunner botRunner;
  private final AutonomousExecutionOrchestrator orchestrator;
  private final CostStore costStore;
  private final Clock clock;

  private final ConcurrentHashMap<UUID, AtomicLong> cyclesMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> decisionsMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> ordersMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> riskRejectsMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> errorsMap = new ConcurrentHashMap<>();

  public CanaryService(
      BotStore botStore,
      AutonomousBotRunner botRunner,
      AutonomousExecutionOrchestrator orchestrator,
      CostStore costStore,
      Clock clock
  ) {
    this.botStore = botStore;
    this.botRunner = botRunner;
    this.orchestrator = orchestrator;
    this.costStore = costStore;
    this.clock = clock;
  }

  public List<CanaryStatus> getCanaryStatus() {
    Instant now = clock.instant();
    List<CanaryStatus> list = new ArrayList<>();

    List<Bot> allBots = botStore.findAll();
    for (Bot b : allBots) {
      if (b.broker() == Broker.ALPACA_PAPER || b.broker() == Broker.BYBIT_DEMO) {
        long cycles = cyclesMap.computeIfAbsent(b.id(), k -> new AtomicLong(0)).get();
        long decisions = decisionsMap.computeIfAbsent(b.id(), k -> new AtomicLong(0)).get();
        long orders = ordersMap.computeIfAbsent(b.id(), k -> new AtomicLong(0)).get();
        long riskRejects = riskRejectsMap.computeIfAbsent(b.id(), k -> new AtomicLong(0)).get();
        long errors = errorsMap.computeIfAbsent(b.id(), k -> new AtomicLong(0)).get();

        CostSummary costSum = costStore != null ? costStore.getCostSummaryByBotId(b.id(), now) : null;
        BigDecimal aiCost = costSum != null ? costSum.grandTotalCostUsd() : BigDecimal.ZERO;

        list.add(new CanaryStatus(
            b.broker().name(),
            b.executionMode().name(),
            b.id(),
            "BTC/USD",
            cycles,
            decisions,
            orders,
            orders, // fills match paper orders
            0,
            riskRejects,
            cycles,
            0,
            errors,
            aiCost,
            0,
            now,
            b.status().name()
        ));
      }
    }
    return list;
  }

  public AutonomousExecutionResult runCanaryCycle(UUID botId) {
    cyclesMap.computeIfAbsent(botId, k -> new AtomicLong(0)).incrementAndGet();
    decisionsMap.computeIfAbsent(botId, k -> new AtomicLong(0)).incrementAndGet();

    try {
      AutonomousExecutionResult res = orchestrator.runCycle(botId);
      if ("EXECUTED".equals(res.status())) {
        ordersMap.computeIfAbsent(botId, k -> new AtomicLong(0)).incrementAndGet();
      } else if ("REJECTED_RISK".equals(res.status())) {
        riskRejectsMap.computeIfAbsent(botId, k -> new AtomicLong(0)).incrementAndGet();
      }
      return res;
    } catch (Exception e) {
      errorsMap.computeIfAbsent(botId, k -> new AtomicLong(0)).incrementAndGet();
      throw e;
    }
  }

  public void startRunner() {
    botRunner.start();
  }

  public void stopRunner() {
    botRunner.stop();
  }
}
