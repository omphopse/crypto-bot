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
  private final io.algopilot.cost.AiCostGovernanceService costGovernance;
  private final AuditEventWriter audit;
  private final Clock clock;

  public LLMDecisionEngineService(
      ContextBuilderService contextBuilder,
      TradingContextStore contextStore,
      LLMDecisionProvider provider,
      StructuredDecisionValidator validator,
      StructuredDecisionStore decisionStore,
      AiCostLimiter costLimiter,
      io.algopilot.cost.AiCostGovernanceService costGovernance,
      AuditEventWriter audit,
      Clock clock
  ) {
    this.contextBuilder = contextBuilder;
    this.contextStore = contextStore;
    this.provider = provider;
    this.validator = validator;
    this.decisionStore = decisionStore;
    this.costLimiter = costLimiter;
    this.costGovernance = costGovernance;
    this.audit = audit;
    this.clock = clock;
  }

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
    this(contextBuilder, contextStore, provider, validator, decisionStore, costLimiter, null, audit, clock);
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

    // 1. Deduplication Check
    if (costGovernance != null && costGovernance.isDuplicateRequest(context)) {
      Optional<StructuredTradeDecision> cachedOpt = costGovernance.getCachedDecision(context);
      if (cachedOpt.isPresent()) {
        log.info("Returning cached decision for duplicate context hash {}", context.contextHash());
        return cachedOpt.get();
      }
    }

    // 2. Budget Governance Gate
    if (costGovernance != null) {
      UUID stratId = context.strategy() != null ? context.strategy().strategyId() : null;
      io.algopilot.cost.BudgetStatus budgetStatus = costGovernance.evaluateBudgetStatus(context.botId(), stratId);
      if (budgetStatus == io.algopilot.cost.BudgetStatus.BLOCKED) {
        log.warn("AI decision analysis blocked by budget governance for bot {}", context.botId());
        StructuredTradeDecision failedDecision = new StructuredTradeDecision(
            UUID.randomUUID(), context.contextId(), context.contextHash(), context.botId(),
            context.agentSessionId(), context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID(),
            provider.providerName(), provider.modelName(), TradeAction.NO_ACTION,
            context.market() != null ? context.market().symbol() : "UNKNOWN",
            "FLAT", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            "INTRADAY", "Analysis skipped due to AI budget policy exhaustion.",
            List.of(), List.of(), List.of(), ValidationStatus.FAILED, "AI_BUDGET_EXCEEDED",
            0L, 0, 0, BigDecimal.ZERO, now, now.plusSeconds(300)
        );
        decisionStore.save(failedDecision);
        audit.record("AGENT", context.botId().toString(), "DECISION_FAILED", "DECISION", failedDecision.id().toString(),
            Map.of("reason", "AI_BUDGET_EXCEEDED"));
        return failedDecision;
      }
    }

    // 3. Rate Limiting & Cost Budget Checks
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

    // 4. Execute LLM Analysis
    long startTime = clock.instant().toEpochMilli();
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
    long latencyMs = clock.instant().toEpochMilli() - startTime;

    // 5. Record Token & Cost Usage
    costLimiter.recordUsage(rawDecision.inputTokens(), rawDecision.outputTokens(), rawDecision.estimatedCostUsd());
    if (costGovernance != null) {
      costGovernance.recordAiDecisionCost(context, rawDecision, provider.providerName(), provider.modelName(), latencyMs);
    }

    // 6. Validate Structured Decision
    StructuredTradeDecision validatedDecision = validator.validate(rawDecision, context);

    // 7. Persist & Cache Decision Record
    decisionStore.save(validatedDecision);
    if (costGovernance != null) {
      costGovernance.cacheDecision(context, validatedDecision);
    }

    // 8. Audit Trail Logging (Zero Execution Allowed)
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
