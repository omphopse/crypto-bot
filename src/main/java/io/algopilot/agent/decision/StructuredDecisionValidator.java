package io.algopilot.agent.decision;

import io.algopilot.agent.context.ResearchEvidenceContext;
import io.algopilot.agent.context.TradingContext;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StructuredDecisionValidator {

  public StructuredTradeDecision validate(StructuredTradeDecision d, TradingContext context) {
    if (d == null) {
      throw new IllegalArgumentException("Decision cannot be null");
    }
    if (context == null) {
      return reject(d, "MISSING_TRADING_CONTEXT");
    }

    // 1. Context ID & Hash Matching
    if (!context.contextId().equals(d.contextId())) {
      return reject(d, "CONTEXT_ID_MISMATCH");
    }
    if (!context.contextHash().equals(d.contextHash())) {
      return reject(d, "CONTEXT_HASH_MISMATCH");
    }

    // 2. Confidence Bounds
    if (d.confidence() == null || d.confidence().compareTo(BigDecimal.ZERO) < 0 || d.confidence().compareTo(BigDecimal.ONE) > 0) {
      return reject(d, "INVALID_CONFIDENCE_BOUNDS");
    }

    // 3. Symbol Matching
    String expectedSymbol = context.market() != null ? context.market().symbol() : null;
    if (expectedSymbol == null || !expectedSymbol.equalsIgnoreCase(d.symbol())) {
      return reject(d, "UNSUPPORTED_SYMBOL");
    }

    // 4. Strategy Version Matching
    if (context.strategy() != null && !context.strategy().strategyVersionId().equals(d.strategyVersionId())) {
      return reject(d, "STRATEGY_VERSION_MISMATCH");
    }

    // 5. Directional Order Sizing & Price
    if (d.decision() == TradeAction.BUY || d.decision() == TradeAction.SELL || d.decision() == TradeAction.REDUCE) {
      if (d.quantity() == null || d.quantity().compareTo(BigDecimal.ZERO) <= 0) {
        return reject(d, "INVALID_NON_POSITIVE_QUANTITY");
      }
      if (d.referencePrice() == null || d.referencePrice().compareTo(BigDecimal.ZERO) <= 0) {
        return reject(d, "INVALID_NON_POSITIVE_PRICE");
      }

      // Stop Loss & Take Profit Sanity
      if (d.decision() == TradeAction.BUY) {
        if (d.stopLoss() != null && d.stopLoss().compareTo(BigDecimal.ZERO) > 0 && d.stopLoss().compareTo(d.referencePrice()) >= 0) {
          return reject(d, "INVALID_BUY_STOP_LOSS_ABOVE_PRICE");
        }
        if (d.takeProfit() != null && d.takeProfit().compareTo(BigDecimal.ZERO) > 0 && d.takeProfit().compareTo(d.referencePrice()) <= 0) {
          return reject(d, "INVALID_BUY_TAKE_PROFIT_BELOW_PRICE");
        }
      } else if (d.decision() == TradeAction.SELL) {
        if (d.stopLoss() != null && d.stopLoss().compareTo(BigDecimal.ZERO) > 0 && d.stopLoss().compareTo(d.referencePrice()) <= 0) {
          return reject(d, "INVALID_SELL_STOP_LOSS_BELOW_PRICE");
        }
        if (d.takeProfit() != null && d.takeProfit().compareTo(BigDecimal.ZERO) > 0 && d.takeProfit().compareTo(d.referencePrice()) >= 0) {
          return reject(d, "INVALID_SELL_TAKE_PROFIT_ABOVE_PRICE");
        }
      }
    }

    // 6. Evidence Reference Provenance
    if (d.evidenceReferences() != null && !d.evidenceReferences().isEmpty()) {
      Set<UUID> validEvIds = new HashSet<>();
      if (context.research() != null) {
        for (ResearchEvidenceContext ev : context.research()) {
          validEvIds.add(ev.evidenceId());
        }
      }
      for (UUID ref : d.evidenceReferences()) {
        if (!validEvIds.contains(ref)) {
          return reject(d, "UNSUPPORTED_SYNTHETIC_EVIDENCE");
        }
      }
    }

    // 7. Safety Gating Observation Check
    if (context.safety() != null && !context.safety().executionAllowed()) {
      if (d.decision() == TradeAction.BUY || d.decision() == TradeAction.SELL) {
        return reject(d, "SAFETY_GATES_DISALLOWED:" + String.join(",", context.safety().safetyBlockReasons()));
      }
    }

    return new StructuredTradeDecision(
        d.id(), d.contextId(), d.contextHash(), d.botId(), d.sessionId(), d.strategyVersionId(),
        d.provider(), d.model(), d.decision(), d.symbol(), d.side(), d.confidence(),
        d.quantity(), d.referencePrice(), d.stopLoss(), d.takeProfit(), d.timeHorizon(),
        d.thesis(), d.evidenceReferences(), d.riskFactors(), d.invalidationConditions(),
        ValidationStatus.VALIDATED, null, d.latencyMs(), d.inputTokens(), d.outputTokens(),
        d.estimatedCostUsd(), d.decisionTimestamp(), d.expiresAt()
    );
  }

  private StructuredTradeDecision reject(StructuredTradeDecision d, String reason) {
    return new StructuredTradeDecision(
        d.id(), d.contextId(), d.contextHash(), d.botId(), d.sessionId(), d.strategyVersionId(),
        d.provider(), d.model(), d.decision(), d.symbol(), d.side(), d.confidence(),
        d.quantity(), d.referencePrice(), d.stopLoss(), d.takeProfit(), d.timeHorizon(),
        d.thesis(), d.evidenceReferences(), d.riskFactors(), d.invalidationConditions(),
        ValidationStatus.REJECTED, reason, d.latencyMs(), d.inputTokens(), d.outputTokens(),
        d.estimatedCostUsd(), d.decisionTimestamp(), d.expiresAt()
    );
  }
}
