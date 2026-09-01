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
    log.info("AlgoPilot initialized in clean state. Active trading bots: 0, positions: 0. Status: CONFIGURATION_REQUIRED.");
  }
}
