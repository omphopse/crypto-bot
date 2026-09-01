package io.algopilot.cost;

import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.decision.StructuredTradeDecision;
import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiCostGovernanceService {
  private static final Logger log = LoggerFactory.getLogger(AiCostGovernanceService.class);

  private final CostStore costStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  // Deduplication cache: key = botId_contextHash_stratVer, value = timestamp
  private final ConcurrentHashMap<String, Instant> deduplicationCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, StructuredTradeDecision> decisionDeduplicationCache = new ConcurrentHashMap<>();

  public AiCostGovernanceService(CostStore costStore, AuditEventWriter audit, Clock clock) {
    this.costStore = costStore;
    this.audit = audit;
    this.clock = clock;
  }

  public BudgetStatus evaluateBudgetStatus(UUID botId, UUID strategyId) {
    try {
      Instant now = clock.instant();
      CostSummary todaySummary = costStore.getCostSummaryToday(now);

      Optional<AiBudgetPolicy> globalPolicyOpt = costStore.findBudgetPolicy(BudgetTier.GLOBAL, "GLOBAL");
      if (globalPolicyOpt.isPresent()) {
        AiBudgetPolicy p = globalPolicyOpt.get();
        if (todaySummary.grandTotalCostUsd().compareTo(p.maxCostPerDay()) >= 0 ||
            todaySummary.totalRequests() >= p.maxRequestsPerDay()) {
          log.warn("AI_BUDGET_BLOCKED: Global daily limit reached (cost={}, max={})", todaySummary.grandTotalCostUsd(), p.maxCostPerDay());
          return BudgetStatus.BLOCKED;
        }

        BigDecimal seventyPct = p.maxCostPerDay().multiply(new BigDecimal("0.70"));
        if (todaySummary.grandTotalCostUsd().compareTo(seventyPct) >= 0) {
          log.info("AI_BUDGET_THROTTLED: 70% threshold crossed (cost={}, 70%={})", todaySummary.grandTotalCostUsd(), seventyPct);
          return BudgetStatus.THROTTLED;
        }
      }

      if (botId != null) {
        CostSummary botSummary = costStore.getCostSummaryByBotId(botId, now);
        Optional<AiBudgetPolicy> botPolicyOpt = costStore.findBudgetPolicy(BudgetTier.BOT, botId.toString());
        if (botPolicyOpt.isPresent()) {
          AiBudgetPolicy bp = botPolicyOpt.get();
          if (botSummary.grandTotalCostUsd().compareTo(bp.maxCostPerDay()) >= 0) {
            log.warn("AI_BUDGET_BLOCKED for bot {}: daily limit reached", botId);
            return BudgetStatus.BLOCKED;
          }
        }
      }

      return BudgetStatus.NORMAL;
    } catch (Exception e) {
      log.error("Cost governance failure - failing closed: {}", e.getMessage());
      return BudgetStatus.BLOCKED;
    }
  }

  public boolean isDuplicateRequest(TradingContext context) {
    if (context == null) return false;
    Instant now = clock.instant();
    String key = context.botId() + "_" + context.contextHash() + "_" +
        (context.strategy() != null ? context.strategy().strategyVersionId() : "none");

    Instant lastSeen = deduplicationCache.get(key);
    if (lastSeen != null && now.toEpochMilli() - lastSeen.toEpochMilli() < 10000) {
      log.info("PREVENTED_DUPLICATE_AI_REQUEST for key={}", key);
      return true;
    }
    deduplicationCache.put(key, now);
    return false;
  }

  public Optional<StructuredTradeDecision> getCachedDecision(TradingContext context) {
    if (context == null) return Optional.empty();
    String key = context.botId() + "_" + context.contextHash() + "_" +
        (context.strategy() != null ? context.strategy().strategyVersionId() : "none");
    return Optional.ofNullable(decisionDeduplicationCache.get(key));
  }

  public void cacheDecision(TradingContext context, StructuredTradeDecision decision) {
    if (context == null || decision == null) return;
    String key = context.botId() + "_" + context.contextHash() + "_" +
        (context.strategy() != null ? context.strategy().strategyVersionId() : "none");
    decisionDeduplicationCache.put(key, decision);
  }

  public AiCostEvent recordAiDecisionCost(
      TradingContext context,
      StructuredTradeDecision decision,
      String provider,
      String model,
      long latencyMs
  ) {
    Instant now = clock.instant();
    UUID costEventId = UUID.randomUUID();

    // Attribution breakdown
    long inTokens = decision != null && decision.inputTokens() > 0 ? decision.inputTokens() : 1200L;
    long outTokens = decision != null && decision.outputTokens() > 0 ? decision.outputTokens() : 180L;
    long totalTokens = inTokens + outTokens;

    Optional<AiModelPricing> pricingOpt = costStore.findActivePricing(provider, model, now);
    BigDecimal inPricePerM = pricingOpt.map(AiModelPricing::inputPricePerMillion).orElse(new BigDecimal("0.50"));
    BigDecimal outPricePerM = pricingOpt.map(AiModelPricing::outputPricePerMillion).orElse(new BigDecimal("1.50"));

    BigDecimal inCost = inPricePerM.multiply(BigDecimal.valueOf(inTokens)).divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP);
    BigDecimal outCost = outPricePerM.multiply(BigDecimal.valueOf(outTokens)).divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP);
    BigDecimal totalCost = inCost.add(outCost);

    String attributionJson = "{" +
        "\"systemInstructions\": 300," +
        "\"marketContext\": 250," +
        "\"strategyContext\": 150," +
        "\"portfolioContext\": 150," +
        "\"riskContext\": 100," +
        "\"researchEvidence\": 250," +
        "\"outputTokens\": " + outTokens +
        "}";

    AiCostEvent event = new AiCostEvent(
        costEventId,
        context != null ? context.botId() : null,
        context != null ? context.agentSessionId() : null,
        context != null && context.strategy() != null ? context.strategy().strategyId() : null,
        context != null && context.strategy() != null ? context.strategy().strategyVersionId() : null,
        context != null ? context.contextId() : null,
        decision != null ? decision.id() : null,
        provider, model, CostOperationType.AI_DECISION,
        inTokens, outTokens, totalTokens,
        inCost, outCost, totalCost,
        "USD", now, latencyMs, BigDecimal.ZERO, attributionJson
    );

    costStore.saveCostEvent(event);
    audit.record("SYSTEM", context != null ? context.botId().toString() : "GLOBAL", "AI_COST_RECORDED", "COST_EVENT", costEventId.toString(),
        Map.of("costUsd", totalCost.toPlainString(), "tokens", String.valueOf(totalTokens)));

    return event;
  }
}
