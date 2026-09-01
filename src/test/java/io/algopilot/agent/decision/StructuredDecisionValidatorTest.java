package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.FreshnessSummary;
import io.algopilot.agent.context.IndicatorContext;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PerformanceContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.ReconciliationContext;
import io.algopilot.agent.context.ResearchEvidenceContext;
import io.algopilot.agent.context.RiskContext;
import io.algopilot.agent.context.SafetySummary;
import io.algopilot.agent.context.ScannerContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.market.scanner.CandidateType;
import io.algopilot.research.model.SecurityStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructuredDecisionValidatorTest {
  private StructuredDecisionValidator validator;
  private TradingContext validContext;
  private UUID contextId;
  private String contextHash;
  private UUID botId;
  private UUID sessionId;
  private UUID stratVersionId;
  private UUID evidenceId;
  private Instant now;

  @BeforeEach
  void setUp() {
    validator = new StructuredDecisionValidator();
    now = Instant.parse("2026-09-01T12:00:00Z");
    contextId = UUID.randomUUID();
    contextHash = "abc123hash";
    botId = UUID.randomUUID();
    sessionId = UUID.randomUUID();
    stratVersionId = UUID.randomUUID();
    evidenceId = UUID.randomUUID();

    MarketContext market = new MarketContext("BTC/USD", "ALPACA_PAPER", "PAPER", new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 1000L, FreshnessStatus.FRESH, "VALID");
    StrategyContext strategy = new StrategyContext(UUID.randomUUID(), stratVersionId, "Test Strat", 1, "BTC/USD", "1m", new ObjectMapper().createObjectNode(), "ACTIVE");
    PortfolioContext portfolio = new PortfolioContext(new BigDecimal("100000"), new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), BigDecimal.ZERO, now);
    RiskContext risk = new RiskContext("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("50000"), false, false, false);
    ReconciliationContext recon = new ReconciliationContext("MATCHED", now, 0, false, false);
    ResearchEvidenceContext ev = new ResearchEvidenceContext(evidenceId, "BTC", "NEWS", "reuters.com", "ETF Inflows continue", new BigDecimal("0.9"), SecurityStatus.CLEAN, now, true);
    SafetySummary safety = new SafetySummary(true, true, true, true, true, true, List.of());
    FreshnessSummary freshness = new FreshnessSummary(1000L, 0L, 0L, FreshnessStatus.FRESH);

    validContext = new TradingContext(
        contextId, contextHash, now, botId, sessionId, "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market,
        null, null, strategy, portfolio, List.of(), List.of(), risk, recon,
        List.of(ev), null, freshness, safety
    );
  }

  @Test
  void testValidate_validBuyDecision_returnsValidated() {
    StructuredTradeDecision d = new StructuredTradeDecision(
        UUID.randomUUID(), contextId, contextHash, botId, sessionId, stratVersionId,
        "DETERMINISTIC_FAKE", "fake-v1", TradeAction.BUY, "BTC/USD", "BUY",
        new BigDecimal("0.85"), new BigDecimal("0.10"), new BigDecimal("60000.00"),
        new BigDecimal("58000.00"), new BigDecimal("63000.00"), "INTRADAY",
        "Bullish momentum aligned", List.of(evidenceId), List.of("Volatility"), List.of("Invalidation"),
        ValidationStatus.VALIDATED, null, 10L, 100, 50, new BigDecimal("0.0001"), now, now.plusSeconds(300)
    );

    StructuredTradeDecision res = validator.validate(d, validContext);
    assertThat(res.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
    assertThat(res.rejectionReason()).isNull();
  }

  @Test
  void testValidate_unsupportedSymbol_returnsRejected() {
    StructuredTradeDecision d = new StructuredTradeDecision(
        UUID.randomUUID(), contextId, contextHash, botId, sessionId, stratVersionId,
        "DETERMINISTIC_FAKE", "fake-v1", TradeAction.BUY, "DOGE/USD", "BUY",
        new BigDecimal("0.85"), new BigDecimal("100"), new BigDecimal("0.10"),
        new BigDecimal("0.09"), new BigDecimal("0.12"), "INTRADAY",
        "Meme rally", List.of(), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 10L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );

    StructuredTradeDecision res = validator.validate(d, validContext);
    assertThat(res.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(res.rejectionReason()).isEqualTo("UNSUPPORTED_SYMBOL");
  }

  @Test
  void testValidate_buyStopLossAbovePrice_returnsRejected() {
    StructuredTradeDecision d = new StructuredTradeDecision(
        UUID.randomUUID(), contextId, contextHash, botId, sessionId, stratVersionId,
        "DETERMINISTIC_FAKE", "fake-v1", TradeAction.BUY, "BTC/USD", "BUY",
        new BigDecimal("0.85"), new BigDecimal("0.10"), new BigDecimal("60000.00"),
        new BigDecimal("61000.00"), // Invalid: stop loss above buy entry!
        new BigDecimal("63000.00"), "INTRADAY",
        "Bullish thesis", List.of(), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 10L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );

    StructuredTradeDecision res = validator.validate(d, validContext);
    assertThat(res.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(res.rejectionReason()).isEqualTo("INVALID_BUY_STOP_LOSS_ABOVE_PRICE");
  }

  @Test
  void testValidate_syntheticUnsupportedEvidence_returnsRejected() {
    UUID fakeEvidenceId = UUID.randomUUID(); // Not in context!
    StructuredTradeDecision d = new StructuredTradeDecision(
        UUID.randomUUID(), contextId, contextHash, botId, sessionId, stratVersionId,
        "DETERMINISTIC_FAKE", "fake-v1", TradeAction.BUY, "BTC/USD", "BUY",
        new BigDecimal("0.85"), new BigDecimal("0.10"), new BigDecimal("60000.00"),
        new BigDecimal("58000.00"), new BigDecimal("63000.00"), "INTRADAY",
        "Bullish thesis using hallucinated evidence", List.of(fakeEvidenceId), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 10L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );

    StructuredTradeDecision res = validator.validate(d, validContext);
    assertThat(res.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(res.rejectionReason()).isEqualTo("UNSUPPORTED_SYNTHETIC_EVIDENCE");
  }

  @Test
  void testValidate_contextHashMismatch_returnsRejected() {
    StructuredTradeDecision d = new StructuredTradeDecision(
        UUID.randomUUID(), contextId, "tampered_hash_xyz", botId, sessionId, stratVersionId,
        "DETERMINISTIC_FAKE", "fake-v1", TradeAction.BUY, "BTC/USD", "BUY",
        new BigDecimal("0.85"), new BigDecimal("0.10"), new BigDecimal("60000.00"),
        new BigDecimal("58000.00"), new BigDecimal("63000.00"), "INTRADAY",
        "Bullish thesis", List.of(), List.of(), List.of(),
        ValidationStatus.VALIDATED, null, 10L, 100, 50, BigDecimal.ZERO, now, now.plusSeconds(300)
    );

    StructuredTradeDecision res = validator.validate(d, validContext);
    assertThat(res.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(res.rejectionReason()).isEqualTo("CONTEXT_HASH_MISMATCH");
  }
}
