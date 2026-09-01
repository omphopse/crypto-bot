package io.algopilot.agent.decision;

import io.algopilot.agent.context.ContextBuilderService;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.context.TradingContextStore;
import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LLMDecisionEngineService {
  private static final Logger log = LoggerFactory.getLogger(LLMDecisionEngineService.class);

  private final ContextBuilderService contextBuilder;
  private final TradingContextStore contextStore;
  private final LLMDecisionProvider provider;
  private final StructuredDecisionValidator validator;
  private final StructuredDecisionStore decisionStore;
  private final AiCostLimiter costLimiter;
  private final AuditEventWriter audit;
  private final Clock clock;

  public LLMDecisionEngineService(
      ContextBuilderService contextBuilder,
      TradingContextStore contextStore,
      LLMDecisionProvider provider,
      StructuredDecisionValidator validator,
      StructuredDecisionStore decisionStore,
      AiCostLimiter costLimiter,
      AuditEventWriter audit,
      Clock clock
  ) {
    this.contextBuilder = contextBuilder;
    this.contextStore = contextStore;
    this.provider = provider;
    this.validator = validator;
    this.decisionStore = decisionStore;
    this.costLimiter = costLimiter;
    this.audit = audit;
    this.clock = clock;
  }

  public StructuredTradeDecision analyzeBot(UUID botId) {
    TradingContext context = contextBuilder.buildContext(botId);
    return analyze(context);
  }

  public StructuredTradeDecision analyze(TradingContext context) {
    Instant now = clock.instant();
    if (context == null) {
      throw new IllegalArgumentException("TradingContext cannot be null");
    }

    // 1. Rate Limiting & Cost Budget Checks
    if (!costLimiter.tryAcquire()) {
      log.warn("AI decision analysis rate limited or budget exceeded for bot {}", context.botId());
      StructuredTradeDecision failedDecision = new StructuredTradeDecision(
          UUID.randomUUID(), context.contextId(), context.contextHash(), context.botId(),
          context.agentSessionId(), context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID(),
          provider.providerName(), provider.modelName(), TradeAction.NO_ACTION,
          context.market() != null ? context.market().symbol() : "UNKNOWN",
          "FLAT", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          "INTRADAY", "Analysis skipped due to AI rate limits or daily cost budget exhaustion.",
          List.of(), List.of(), List.of(), ValidationStatus.FAILED, "AI_RATE_OR_BUDGET_EXCEEDED",
          0L, 0, 0, BigDecimal.ZERO, now, now.plusSeconds(300)
      );
      decisionStore.save(failedDecision);
      audit.record("AGENT", context.botId().toString(), "DECISION_FAILED", "DECISION", failedDecision.id().toString(),
          Map.of("reason", "AI_RATE_OR_BUDGET_EXCEEDED"));
      return failedDecision;
    }

    // 2. Execute LLM Analysis
    StructuredTradeDecision rawDecision;
    try {
      rawDecision = provider.analyze(context);
    } catch (Exception e) {
      log.error("LLM Provider failed during context analysis: {}", e.getMessage(), e);
      StructuredTradeDecision errorDecision = new StructuredTradeDecision(
          UUID.randomUUID(), context.contextId(), context.contextHash(), context.botId(),
          context.agentSessionId(), context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID(),
          provider.providerName(), provider.modelName(), TradeAction.NO_ACTION,
          context.market() != null ? context.market().symbol() : "UNKNOWN",
          "FLAT", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          "INTRADAY", "LLM reasoning provider failure: " + e.getMessage(),
          List.of(), List.of(), List.of(), ValidationStatus.FAILED, "PROVIDER_ERROR:" + e.getClass().getSimpleName(),
          0L, 0, 0, BigDecimal.ZERO, now, now.plusSeconds(300)
      );
      decisionStore.save(errorDecision);
      audit.record("AGENT", context.botId().toString(), "DECISION_FAILED", "DECISION", errorDecision.id().toString(),
          Map.of("error", e.getMessage() != null ? e.getMessage() : "PROVIDER_ERROR"));
      return errorDecision;
    }

    // 3. Record Token & Cost Usage
    costLimiter.recordUsage(rawDecision.inputTokens(), rawDecision.outputTokens(), rawDecision.estimatedCostUsd());

    // 4. Validate Structured Decision
    StructuredTradeDecision validatedDecision = validator.validate(rawDecision, context);

    // 5. Persist Decision Record
    decisionStore.save(validatedDecision);

    // 6. Audit Trail Logging (Zero Execution Allowed)
    String eventType = validatedDecision.validationStatus() == ValidationStatus.VALIDATED
        ? "DECISION_VALIDATED"
        : "DECISION_REJECTED";

    audit.record("AGENT", context.botId().toString(), eventType, "DECISION", validatedDecision.id().toString(),
        Map.of(
            "decision", validatedDecision.decision().name(),
            "symbol", validatedDecision.symbol(),
            "confidence", validatedDecision.confidence().toPlainString(),
            "validationStatus", validatedDecision.validationStatus().name(),
            "rejectionReason", validatedDecision.rejectionReason() != null ? validatedDecision.rejectionReason() : "NONE"
        ));

    return validatedDecision;
  }
}
