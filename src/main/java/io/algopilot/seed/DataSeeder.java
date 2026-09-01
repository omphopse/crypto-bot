package io.algopilot.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.AgentDecision;
import io.algopilot.agent.AgentDecisionStore;
import io.algopilot.agent.DecisionAction;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.strategy.Strategy;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final BotStore botStore;
  private final StrategyStore strategyStore;
  private final PositionStore positionStore;
  private final AgentDecisionStore decisionStore;
  private final ObjectMapper json;

  public DataSeeder(
      BotStore botStore,
      StrategyStore strategyStore,
      PositionStore positionStore,
      AgentDecisionStore decisionStore,
      ObjectMapper json) {
    this.botStore = botStore;
    this.strategyStore = strategyStore;
    this.positionStore = positionStore;
    this.decisionStore = decisionStore;
    this.json = json;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!botStore.findAll().isEmpty()) {
      return;
    }

    log.info("Seeding initial Algopilot trading bots, strategies, positions and decisions...");

    // 1. Create Canary Alpaca Paper Strategy & Bot
    UUID strat1Id = UUID.randomUUID();
    strategyStore.saveStrategy(new Strategy(strat1Id, "Canary Alpaca Paper", "DEPLOYED", Instant.now()));
    UUID strat1VerId = UUID.randomUUID();
    strategyStore.saveVersion(new StrategyVersion(
        strat1VerId,
        strat1Id,
        1,
        json.valueToTree(Map.of("type", "MOMENTUM", "timeframe", "5m", "rsiPeriod", 14, "takeProfitPct", 0.04, "stopLossPct", 0.02)),
        "Canary Alpaca Paper momentum verification model",
        Instant.now()
    ));
    UUID bot1Id = UUID.randomUUID();
    botStore.save(new Bot(bot1Id, "Canary Alpaca Paper", strat1VerId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, Instant.now()));

    // 2. Create US Equity Trend Strategy & Bot (Paused by default for safety isolation)
    UUID strat2Id = UUID.randomUUID();
    strategyStore.saveStrategy(new Strategy(strat2Id, "US Equity Trend", "DEPLOYED", Instant.now()));
    UUID strat2VerId = UUID.randomUUID();
    strategyStore.saveVersion(new StrategyVersion(
        strat2VerId,
        strat2Id,
        1,
        json.valueToTree(Map.of("type", "TREND_FOLLOWING", "timeframe", "15m", "fastEma", 20, "slowEma", 50, "trailStopPct", 0.03)),
        "Initial US Equity Trend v2 deployment",
        Instant.now()
    ));
    UUID bot2Id = UUID.randomUUID();
    botStore.save(new Bot(bot2Id, "US Equity Trend", strat2VerId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.PAUSED, Instant.now()));

    // 3. Create Canary Bybit Demo Strategy & Bot (Paused by default for safety isolation)
    UUID strat3Id = UUID.randomUUID();
    strategyStore.saveStrategy(new Strategy(strat3Id, "Canary Bybit Demo", "DEPLOYED", Instant.now()));
    UUID strat3VerId = UUID.randomUUID();
    strategyStore.saveVersion(new StrategyVersion(
        strat3VerId,
        strat3Id,
        1,
        json.valueToTree(Map.of("type", "MEAN_REVERSION", "timeframe", "1h", "bbPeriod", 20, "bbStdDev", 2.0)),
        "Canary Bybit Demo mean-reversion model",
        Instant.now()
    ));
    UUID bot3Id = UUID.randomUUID();
    botStore.save(new Bot(bot3Id, "Canary Bybit Demo", strat3VerId, Broker.BYBIT_DEMO, ExecutionMode.DEMO, BotStatus.PAUSED, Instant.now()));

    // 4. Seed Open Positions
    positionStore.save(new Position(UUID.randomUUID(), bot1Id.toString(), "BTC/USD", new BigDecimal("0.184"), new BigDecimal("112408.20"), new BigDecimal("146.52"), Instant.now()));
    positionStore.save(new Position(UUID.randomUUID(), bot2Id.toString(), "NVDA", new BigDecimal("82"), new BigDecimal("181.42"), new BigDecimal("183.68"), Instant.now()));
    positionStore.save(new Position(UUID.randomUUID(), bot3Id.toString(), "ETH/USD", new BigDecimal("5.20"), new BigDecimal("4192.30"), new BigDecimal("-125.84"), Instant.now()));

    // 5. Seed Initial Decision Journal Entries
    decisionStore.save(new AgentDecision(
        UUID.randomUUID(),
        bot1Id,
        strat1VerId,
        DecisionAction.HOLD,
        "BTC/USD",
        "{\"rationale\":\"Momentum score 0.74 above threshold. Trailing stop held at $109,500.\"}",
        Instant.now().minusSeconds(120)
    ));
    decisionStore.save(new AgentDecision(
        UUID.randomUUID(),
        bot2Id,
        strat2VerId,
        DecisionAction.BUY,
        "NVDA",
        "{\"rationale\":\"EMA 20/50 golden cross confirmed on 15m candle. Entry size 82 units.\"}",
        Instant.now().minusSeconds(300)
    ));
    decisionStore.save(new AgentDecision(
        UUID.randomUUID(),
        bot3Id,
        strat3VerId,
        DecisionAction.HOLD,
        "ETH/USD",
        "{\"rationale\":\"Price at lower Bollinger Band (2.1 std). Monitoring for mean-reversion signal trigger.\"}",
        Instant.now().minusSeconds(60)
    ));

    log.info("Initial trading bots, strategies, positions and decisions seeded successfully.");
  }
}
