package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only connectivity and schema verification test for local Ollama daemon.
 * Queries local endpoint http://127.0.0.1:11434 if available.
 * Strictly read-only: never places broker orders, starts no bots, starts no canaries.
 */
class OllamaConnectivityIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(OllamaConnectivityIntegrationTest.class);

  private ObjectMapper json;
  private Clock clock;
  private TradingContext testContext;

  @BeforeEach
  void setUp() {
    json = new ObjectMapper();
    clock = Clock.fixed(Instant.parse("2026-09-09T18:00:00Z"), ZoneOffset.UTC);

    UUID botId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID stratVerId = UUID.randomUUID();

    MarketContext market = new MarketContext(
        "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("60000.00"), new BigDecimal("59990.00"), new BigDecimal("60010.00"), new BigDecimal("20.00"),
        new BigDecimal("10.0"), new BigDecimal("59900.00"), new BigDecimal("60100.00"), new BigDecimal("59800.00"),
        new BigDecimal("60000.00"), "1m", clock.instant(), clock.instant(), 100L, FreshnessStatus.FRESH, "VALID"
    );
    StrategyContext strategy = new StrategyContext(UUID.randomUUID(), stratVerId, "Overnight Momentum", 1, "BTC/USD", "15m", json.createObjectNode(), "ACTIVE");

    testContext = new TradingContext(
        UUID.randomUUID(), "ollama-connectivity-hash", clock.instant(), botId, sessionId, "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market, null, null, strategy, null,
        List.of(), List.of(), null, null, List.of(), null, null, null
    );
  }

  @Test
  void testOllamaLocalConnectivityProbe() {
    String baseUrl = System.getenv("OLLAMA_BASE_URL") != null ? System.getenv("OLLAMA_BASE_URL") : "http://127.0.0.1:11434";
    String model = System.getenv("OLLAMA_MODEL") != null ? System.getenv("OLLAMA_MODEL") : "gemma3:4b";

    // 1. Probe root endpoint to check if Ollama server is running locally
    boolean isRunning = false;
    try {
      HttpClient probeClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      HttpRequest probeReq = HttpRequest.newBuilder().uri(URI.create(baseUrl)).GET().build();
      HttpResponse<String> probeResp = probeClient.send(probeReq, HttpResponse.BodyHandlers.ofString());
      if (probeResp.statusCode() == 200 && probeResp.body().contains("Ollama is running")) {
        isRunning = true;
      }
    } catch (Exception ignored) {
      log.info("Local Ollama daemon is not responding on {}. Skipping live integration probe.", baseUrl);
    }

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        baseUrl,
        model,
        45,
        new DecisionPromptBuilder(),
        json,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        clock
    );

    assertThat(provider.providerName()).isEqualTo("ollama");
    assertThat(provider.modelName()).isEqualTo(model);

    if (!isRunning) {
      log.info("Verifying fail-safe contract for offline Ollama state.");
      StructuredTradeDecision decision = provider.analyze(testContext);
      assertThat(decision).isNotNull();
      assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
      assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
      assertThat(decision.isActionable()).isFalse();
      return;
    }

    log.info("Ollama is running on {}. Executing read-only inference probe with model: {}", baseUrl, model);
    StructuredTradeDecision decision = provider.analyze(testContext);

    assertThat(decision).isNotNull();
    assertThat(decision.provider()).isEqualTo("ollama");
    assertThat(decision.model()).isEqualTo(model);

    log.info("Live Ollama response received: decision={} side={} confidence={} status={} reason={}",
        decision.decision(), decision.side(), decision.confidence(), decision.validationStatus(), decision.rejectionReason());

    // Evaluate through deterministic validator
    StructuredDecisionValidator validator = new StructuredDecisionValidator();
    StructuredTradeDecision validatedDecision = validator.validate(decision, testContext);

    assertThat(validatedDecision).isNotNull();
    log.info("Deterministic validator result: status={} actionable={}",
        validatedDecision.validationStatus(), validatedDecision.isActionable());

    // The decision remains an advisory analysis; zero orders placed, live trading remains disabled
    assertThat(validatedDecision.decision()).isIn(
        TradeAction.BUY, TradeAction.SELL, TradeAction.HOLD, TradeAction.CLOSE, TradeAction.REDUCE, TradeAction.NO_ACTION
    );
  }
}
