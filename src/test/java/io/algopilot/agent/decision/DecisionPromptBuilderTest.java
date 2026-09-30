package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.IndicatorContext;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.PortfolioContext;
import io.algopilot.agent.context.PositionContext;
import io.algopilot.agent.context.ScannerContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import io.algopilot.market.scanner.CandidateType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionPromptBuilderTest {

  private DecisionPromptBuilder promptBuilder;
  private ObjectMapper json;
  private Instant now;

  @BeforeEach
  void setUp() {
    promptBuilder = new DecisionPromptBuilder();
    json = new ObjectMapper();
    now = Instant.parse("2026-09-10T12:00:00Z");
  }

  @Test
  void shouldRenderAllIndicatorsAndMomentumRulesWhenPresent() {
    ObjectNode params = json.createObjectNode();
    params.put("fastEma", 12);
    params.put("slowEma", 26);
    params.put("rsiPeriod", 14);

    MarketContext market = new MarketContext(
        "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("78200.00"), new BigDecimal("78195.00"), new BigDecimal("78205.00"), new BigDecimal("10.00"),
        new BigDecimal("5.5"), new BigDecimal("78100.00"), new BigDecimal("78300.00"), new BigDecimal("78050.00"),
        new BigDecimal("78200.00"), "1m", now, now, 50L, FreshnessStatus.FRESH, "VALID"
    );

    StrategyContext strategy = new StrategyContext(
        UUID.randomUUID(), UUID.randomUUID(), "MOMENTUM", 1, "BTC/USD", "1m", params, "ACTIVE"
    );

    IndicatorContext indicators = new IndicatorContext(
        "BTC/USD", "1m",
        new BigDecimal("78250.00"), // fast
        new BigDecimal("78150.00"), // slow
        new BigDecimal("78000.00"), // sma50
        new BigDecimal("77500.00"), // sma200
        new BigDecimal("58.50"),    // rsi14
        new BigDecimal("100.00"),   // macd
        new BigDecimal("80.00"),    // macdSignal
        new BigDecimal("20.00"),    // macdHist
        new BigDecimal("150.00"),   // atr14
        new BigDecimal("78500.00"), // bbUpper
        new BigDecimal("78200.00"), // bbMiddle
        new BigDecimal("77900.00"), // bbLower
        new BigDecimal("12.0"),     // avgVol
        new BigDecimal("15.0"),     // curVol
        new BigDecimal("0.35"),     // priceChangePct
        new BigDecimal("0.0019"),   // volatility
        true,                       // isWarmedUp
        now
    );

    ScannerContext scanner = new ScannerContext(
        CandidateType.MOMENTUM, new BigDecimal("0.85"),
        List.of("EMA12_ABOVE_EMA26", "RSI_BULLISH_ZONE"), "Bullish momentum detected", "ACTIVE", now
    );

    PortfolioContext portfolio = new PortfolioContext(
        new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        new BigDecimal("100.00"), BigDecimal.ZERO, now
    );

    PositionContext position = new PositionContext(
        "BTC/USD", "LONG", new BigDecimal("0.00013"), new BigDecimal("78000.00"),
        new BigDecimal("78200.00"), new BigDecimal("10.17"), new BigDecimal("10.14"),
        new BigDecimal("0.03"), BigDecimal.ZERO
    );

    TradingContext ctx = new TradingContext(
        UUID.randomUUID(), "hash-123", now, UUID.randomUUID(), UUID.randomUUID(),
        "ALPACA_PAPER", "PAPER", AgentState.ANALYZING, AutonomousMode.PAPER_AUTONOMOUS,
        market, indicators, scanner, strategy, portfolio, List.of(position), List.of(),
        null, null, List.of(), null, null, null
    );

    String prompt = promptBuilder.buildPrompt(ctx);

    // Verify indicators section rendered
    assertThat(prompt).contains("=== QUANTITATIVE TECHNICAL INDICATORS ===");
    assertThat(prompt).contains("Warmed Up: true");
    assertThat(prompt).contains("Fast EMA: 78250.00");
    assertThat(prompt).contains("Slow EMA: 78150.00");
    assertThat(prompt).contains("EMA Spread: 100.00 (BULLISH)");
    assertThat(prompt).contains("RSI(14): 58.50");
    assertThat(prompt).contains("MACD: 100.00 (Signal: 80.00, Hist: 20.00)");
    assertThat(prompt).contains("Bollinger Bands: Upper=78500.00, Middle=78200.00, Lower=77900.00");
    assertThat(prompt).contains("Price Change %: 0.35%");

    // Verify strategy parameters rendered
    assertThat(prompt).contains("Parameters: {\"fastEma\":12,\"slowEma\":26,\"rsiPeriod\":14}");

    // Verify positions rendered
    assertThat(prompt).contains("Active Positions:");
    assertThat(prompt).contains("BTC/USD LONG qty=0.00013");

    // Verify momentum rules rendered
    assertThat(prompt).contains("=== STRATEGY HYPOTHESIS CONDITIONS (MOMENTUM) ===");
    assertThat(prompt).contains("BUY HYPOTHESIS: When Indicators are Warmed Up, Fast EMA > Slow EMA");
    assertThat(prompt).contains("SELL/CLOSE HYPOTHESIS: When an existing long position exists and Fast EMA < Slow EMA");
    assertThat(prompt).contains("NO_ACTION: When Indicators are NOT Warmed Up");

    // Verify scanner triggers rendered
    assertThat(prompt).contains("Candidate Type: MOMENTUM");
    assertThat(prompt).contains("Triggers: [EMA12_ABOVE_EMA26, RSI_BULLISH_ZONE]");
  }

  @Test
  void shouldRenderGracefullyWhenIndicatorsNullAndPositionsEmpty() {
    TradingContext ctx = new TradingContext(
        UUID.randomUUID(), "hash-empty", now, UUID.randomUUID(), UUID.randomUUID(),
        "ALPACA_PAPER", "PAPER", AgentState.ANALYZING, AutonomousMode.PAPER_AUTONOMOUS,
        null, null, null, null, null, List.of(), List.of(),
        null, null, List.of(), null, null, null
    );

    String prompt = promptBuilder.buildPrompt(ctx);

    assertThat(prompt).contains("Indicators not available.");
    assertThat(prompt).contains("Active Positions: None (FLAT)");
    assertThat(prompt).contains("=== STRATEGY HYPOTHESIS CONDITIONS (MOMENTUM) ===");
  }
}