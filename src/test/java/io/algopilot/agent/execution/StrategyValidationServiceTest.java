package io.algopilot.agent.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.FreshnessSummary;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.ReconciliationContext;
import io.algopilot.agent.context.RiskContext;
import io.algopilot.agent.context.SafetySummary;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrategyValidationServiceTest {
  private BotStore botStore;
  private StrategyStore strategyStore;
  private MemoryAutonomousExecutionStore executionStore;
  private AuditEventWriter audit;
  private Clock clock;
  private StrategyValidationService service;

  private UUID botId;
  private UUID stratVersionId;
  private UUID stratId;
  private Instant now;
  private TradingContext validContext;

  @BeforeEach
  void setUp() {
    botStore = mock(BotStore.class);
    strategyStore = mock(StrategyStore.class);
    executionStore = new MemoryAutonomousExecutionStore();
    audit = mock(AuditEventWriter.class);
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    service = new StrategyValidationService(botStore, strategyStore, executionStore, audit, clock);

    botId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();
    stratId = UUID.randomUUID();

    Bot bot = new Bot(botId, "Canary Bot", stratVersionId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(bot));

    ObjectNode def = new ObjectMapper().createObjectNode().put("symbol", "BTC/USD");
    StrategyVersion sv = new StrategyVersion(stratVersionId, stratId, 1, def, "initial", now);
    when(strategyStore.findVersionById(stratVersionId)).thenReturn(Optional.of(sv));

    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    StrategyContext strategy = new StrategyContext(stratId, stratVersionId, "Canary Strategy", 1, "BTC/USD", "1m", def, "ACTIVE");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    validContext = new TradingContext(
        UUID.randomUUID(), "hash123", now, botId, UUID.randomUUID(), "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, strategy, portfolio, List.of(), List.of(), risk, recon,
        List.of(), null, freshness, safety
    );
  }

  @Test
  void testValidate_validIntent_passes() {
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        UUID.randomUUID(), UUID.randomUUID(), validContext.contextId(), validContext.contextHash(),
        botId, UUID.randomUUID(), stratId, stratVersionId, "BTC/USD", TradeAction.BUY,
        "BUY", new BigDecimal("0.10"), new BigDecimal("60000.00"), new BigDecimal("58000.00"),
        new BigDecimal("63000.00"), "INTRADAY", now, now.plusSeconds(300)
    );

    StrategyValidationResult res = service.validate(intent, validContext);
    assertThat(res.passed()).isTrue();
    assertThat(res.reason()).isNull();

    verify(audit).record(eq("AGENT"), eq(botId.toString()), eq("STRATEGY_VALIDATION_PASSED"), eq("INTENT"), eq(intent.intentId().toString()), anyMap());
  }

  @Test
  void testValidate_expiredIntent_rejects() {
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        UUID.randomUUID(), UUID.randomUUID(), validContext.contextId(), validContext.contextHash(),
        botId, UUID.randomUUID(), stratId, stratVersionId, "BTC/USD", TradeAction.BUY,
        "BUY", new BigDecimal("0.10"), new BigDecimal("60000.00"), new BigDecimal("58000.00"),
        new BigDecimal("63000.00"), "INTRADAY", now.minusSeconds(400), now.minusSeconds(100) // Expired!
    );

    StrategyValidationResult res = service.validate(intent, validContext);
    assertThat(res.passed()).isFalse();
    assertThat(res.reason()).isEqualTo("DECISION_EXPIRED");
  }

  @Test
  void testValidate_strategyVersionMismatch_rejects() {
    UUID oldVersionId = UUID.randomUUID();
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        UUID.randomUUID(), UUID.randomUUID(), validContext.contextId(), validContext.contextHash(),
        botId, UUID.randomUUID(), stratId, oldVersionId, "BTC/USD", TradeAction.BUY,
        "BUY", new BigDecimal("0.10"), new BigDecimal("60000.00"), new BigDecimal("58000.00"),
        new BigDecimal("63000.00"), "INTRADAY", now, now.plusSeconds(300)
    );

    StrategyValidationResult res = service.validate(intent, validContext);
    assertThat(res.passed()).isFalse();
    assertThat(res.reason()).isEqualTo("STRATEGY_VERSION_MISMATCH");
  }

  @Test
  void testValidate_priceDeviationExceeded_rejects() {
    // Current price is 60000.00; reference price 61000.00 is > 1.6% deviation (> 0.25% limit)
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        UUID.randomUUID(), UUID.randomUUID(), validContext.contextId(), validContext.contextHash(),
        botId, UUID.randomUUID(), stratId, stratVersionId, "BTC/USD", TradeAction.BUY,
        "BUY", new BigDecimal("0.10"), new BigDecimal("61000.00"), new BigDecimal("58000.00"),
        new BigDecimal("63000.00"), "INTRADAY", now, now.plusSeconds(300)
    );

    StrategyValidationResult res = service.validate(intent, validContext);
    assertThat(res.passed()).isFalse();
    assertThat(res.reason()).startsWith("DECISION_PRICE_DEVIATION_EXCEEDED");
  }

  @Test
  void testValidate_exitActionWithoutPosition_rejects() {
    ValidatedTradeIntent intent = new ValidatedTradeIntent(
        UUID.randomUUID(), UUID.randomUUID(), validContext.contextId(), validContext.contextHash(),
        botId, UUID.randomUUID(), stratId, stratVersionId, "BTC/USD", TradeAction.CLOSE,
        "SELL", new BigDecimal("0.10"), new BigDecimal("60000.00"), BigDecimal.ZERO,
        BigDecimal.ZERO, "INTRADAY", now, now.plusSeconds(300)
    );

    StrategyValidationResult res = service.validate(intent, validContext);
    assertThat(res.passed()).isFalse();
    assertThat(res.reason()).isEqualTo("POSITION_NOT_FOUND_FOR_EXIT");
  }

  private static final class MemoryAutonomousExecutionStore implements AutonomousExecutionStore {
    private final Map<UUID, ValidatedTradeIntent> intents = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, StrategyValidationResult> validations = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, AutonomousExecutionResult> executions = Collections.synchronizedMap(new HashMap<>());

    @Override public ValidatedTradeIntent saveIntent(ValidatedTradeIntent intent) { intents.put(intent.intentId(), intent); return intent; }
    @Override public StrategyValidationResult saveValidation(StrategyValidationResult result) { validations.put(result.id(), result); return result; }
    @Override public AutonomousExecutionResult saveExecution(AutonomousExecutionResult execution) { executions.put(execution.id(), execution); return execution; }
    @Override public List<AutonomousExecutionResult> findRecentExecutionsByBotId(UUID botId, int limit) { return executions.values().stream().limit(limit).toList(); }
    @Override public Optional<AutonomousExecutionResult> findLatestExecutionByBotId(UUID botId) { return executions.values().stream().findFirst(); }
  }
}
