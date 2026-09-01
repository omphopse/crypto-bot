package io.algopilot.agent.decision;

import io.algopilot.agent.context.ResearchEvidenceContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.market.scanner.CandidateType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakeLLMDecisionProvider implements LLMDecisionProvider {

  @Override
  public String providerName() {
    return "DETERMINISTIC_FAKE";
  }

  @Override
  public String modelName() {
    return "fake-reasoner-v1";
  }

  @Override
  public StructuredTradeDecision analyze(TradingContext context) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(300); // 5 minutes freshness window

    String symbol = context.market() != null ? context.market().symbol() : "UNKNOWN";
    BigDecimal price = context.market() != null ? context.market().lastPrice() : BigDecimal.ZERO;
    CandidateType scanType = context.scanner() != null ? context.scanner().candidateType() : CandidateType.NO_CANDIDATE;

    TradeAction action = TradeAction.NO_ACTION;
    String side = "FLAT";
    BigDecimal confidence = new BigDecimal("0.50");
    BigDecimal qty = BigDecimal.ZERO;
    BigDecimal sl = BigDecimal.ZERO;
    BigDecimal tp = BigDecimal.ZERO;
    String thesis = "Market conditions do not warrant proactive directional positioning.";
    List<String> riskFactors = new ArrayList<>(List.of("Market volatility", "Execution slippage"));
    List<String> invalidation = new ArrayList<>(List.of("Break of trendline", "Orderbook imbalance"));
    List<UUID> evidenceRefs = new ArrayList<>();

    if (context.research() != null) {
      for (ResearchEvidenceContext ev : context.research()) {
        evidenceRefs.add(ev.evidenceId());
      }
    }

    if (scanType == CandidateType.MOMENTUM || scanType == CandidateType.BREAKOUT) {
      action = TradeAction.BUY;
      side = "BUY";
      confidence = new BigDecimal("0.85");
      qty = new BigDecimal("0.10");
      sl = price.multiply(new BigDecimal("0.98")).setScale(2, RoundingMode.HALF_UP);
      tp = price.multiply(new BigDecimal("1.04")).setScale(2, RoundingMode.HALF_UP);
      thesis = "Deterministic scanner identified bullish breakout/momentum with aligned technical indicators.";
    } else if (scanType == CandidateType.OVERBOUGHT) {
      action = TradeAction.REDUCE;
      side = "SELL";
      confidence = new BigDecimal("0.75");
      qty = new BigDecimal("0.05");
      sl = price.multiply(new BigDecimal("1.02")).setScale(2, RoundingMode.HALF_UP);
      tp = price.multiply(new BigDecimal("0.96")).setScale(2, RoundingMode.HALF_UP);
      thesis = "RSI indicator indicates overbought exhaustion; recommend risk de-escalation.";
    }

    return new StructuredTradeDecision(
        UUID.randomUUID(),
        context.contextId(),
        context.contextHash(),
        context.botId(),
        context.agentSessionId(),
        context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID(),
        providerName(),
        modelName(),
        action,
        symbol,
        side,
        confidence,
        qty,
        price,
        sl,
        tp,
        "INTRADAY",
        thesis,
        evidenceRefs,
        riskFactors,
        invalidation,
        ValidationStatus.VALIDATED,
        null,
        15L, // 15ms latency
        350, // 350 input tokens
        120, // 120 output tokens
        new BigDecimal("0.000450"), // estimated cost
        now,
        expiresAt
    );
  }
}
