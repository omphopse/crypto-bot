package io.algopilot.agent.execution;

import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.TradeAction;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStore;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StrategyValidationService {
  private static final Logger log = LoggerFactory.getLogger(StrategyValidationService.class);
  private static final BigDecimal MAX_PRICE_DEVIATION_PCT = new BigDecimal("0.0025"); // 0.25%

  private final BotStore botStore;
  private final StrategyStore strategyStore;
  private final AutonomousExecutionStore executionStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  public StrategyValidationService(
      BotStore botStore,
      StrategyStore strategyStore,
      AutonomousExecutionStore executionStore,
      AuditEventWriter audit,
      Clock clock
  ) {
    this.botStore = botStore;
    this.strategyStore = strategyStore;
    this.executionStore = executionStore;
    this.audit = audit;
    this.clock = clock;
  }

  public StrategyValidationResult validate(ValidatedTradeIntent intent, TradingContext context) {
    Instant now = clock.instant();
    audit.record("AGENT", intent.botId().toString(), "STRATEGY_VALIDATION_STARTED", "INTENT", intent.intentId().toString(), Map.of());

    // 1. Expiration Check
    if (intent.isExpired(now)) {
      return reject(intent, "DECISION_EXPIRED");
    }

    // 2. Safety Summary Check
    if (context.safety() != null && !context.safety().executionAllowed()) {
      return reject(intent, "SAFETY_GATES_DISALLOWED:" + String.join(",", context.safety().safetyBlockReasons()));
    }

    // 3. Bot Deployed Strategy Version Matching
    Optional<Bot> botOpt = botStore.findById(intent.botId());
    if (botOpt.isEmpty()) {
      return reject(intent, "BOT_NOT_FOUND");
    }
    Bot bot = botOpt.get();
    if (!bot.strategyVersionId().equals(intent.strategyVersionId())) {
      return reject(intent, "STRATEGY_VERSION_MISMATCH");
    }

    // 4. Strategy Existence in Store
    Optional<StrategyVersion> stratOpt = strategyStore.findVersionById(intent.strategyVersionId());
    if (stratOpt.isEmpty()) {
      return reject(intent, "STRATEGY_VERSION_NOT_FOUND");
    }

    // 5. Price Deviation Check (Max 0.25%)
    if (context.market() != null && context.market().lastPrice().compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal currentPrice = context.market().lastPrice();
      BigDecimal diff = intent.referencePrice().subtract(currentPrice).abs();
      BigDecimal deviation = diff.divide(currentPrice, 6, RoundingMode.HALF_UP);
      if (deviation.compareTo(MAX_PRICE_DEVIATION_PCT) > 0) {
        return reject(intent, "DECISION_PRICE_DEVIATION_EXCEEDED:" + deviation.toPlainString());
      }
    }

    // 6. Position State Integrity
    Optional<PositionContext> posOpt = context.positions().stream()
        .filter(p -> p.symbol().equalsIgnoreCase(intent.symbol()))
        .findFirst();

    if (intent.action() == TradeAction.CLOSE || intent.action() == TradeAction.REDUCE) {
      if (posOpt.isEmpty() || posOpt.get().quantity().compareTo(BigDecimal.ZERO) == 0) {
        return reject(intent, "POSITION_NOT_FOUND_FOR_EXIT");
      }
      PositionContext pos = posOpt.get();
      if (intent.quantity().compareTo(pos.quantity().abs()) > 0) {
        return reject(intent, "REDUCE_QUANTITY_EXCEEDS_POSITION");
      }
    }

    // 7. Deterministic Entry / Indicator Sanity
    if (intent.action() == TradeAction.BUY && context.indicators() != null) {
      if (context.indicators().rsi14() != null && context.indicators().rsi14().compareTo(new BigDecimal("80.00")) > 0) {
        return reject(intent, "ENTRY_CONDITION_FAILED_RSI_OVERBOUGHT");
      }
    }

    StrategyValidationResult passedResult = new StrategyValidationResult(
        UUID.randomUUID(), intent.intentId(), intent.decisionId(), true, null, now
    );
    executionStore.saveValidation(passedResult);

    audit.record("AGENT", intent.botId().toString(), "STRATEGY_VALIDATION_PASSED", "INTENT", intent.intentId().toString(),
        Map.of("action", intent.action().name(), "symbol", intent.symbol()));

    return passedResult;
  }

  private StrategyValidationResult reject(ValidatedTradeIntent intent, String reason) {
    log.warn("Strategy validation rejected intent {} for bot {}: {}", intent.intentId(), intent.botId(), reason);
    StrategyValidationResult rejectedResult = new StrategyValidationResult(
        UUID.randomUUID(), intent.intentId(), intent.decisionId(), false, reason, clock.instant()
    );
    executionStore.saveValidation(rejectedResult);

    audit.record("AGENT", intent.botId().toString(), "STRATEGY_VALIDATION_REJECTED", "INTENT", intent.intentId().toString(),
        Map.of("reason", reason));

    return rejectedResult;
  }
}
