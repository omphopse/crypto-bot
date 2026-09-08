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
 * Standalone live connectivity and schema verification test for xAI / Grok API.
 * Runs live network probe if XAI_API_KEY is set in the environment.
 * Never executes trades, places orders, or leaks credentials.
 */
class XaiConnectivityIntegrationTest {
  private static final Logger log = LoggerFactory.getLogger(XaiConnectivityIntegrationTest.class);

  private ObjectMapper json;
  private Clock clock;
  private TradingContext testContext;

  @BeforeEach
  void setUp() {
    json = new ObjectMapper();
    clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);

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
        UUID.randomUUID(), "connectivity-hash", clock.instant(), botId, sessionId, "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market, null, null, strategy, null,
        List.of(), List.of(), null, null, List.of(), null, null, null
    );
  }

  @Test
  void testGrokProvider_instantiationAndFailSafeContract() {
    String apiKey = System.getenv("XAI_API_KEY");
    String baseUrl = System.getenv("XAI_BASE_URL") != null ? System.getenv("XAI_BASE_URL") : "https://api.x.ai/v1";
    String model = System.getenv("XAI_MODEL") != null ? System.getenv("XAI_MODEL") : "grok-2-latest";

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider(
        apiKey,
        baseUrl,
        model,
        new DecisionPromptBuilder(),
        json,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        clock
    );

    assertThat(provider.providerName()).isEqualTo("XAI_GROK");
    assertThat(provider.modelName()).isEqualTo(model);

    if (apiKey == null || apiKey.isBlank()) {
      log.info("XAI_API_KEY is not set in environment. Verifying fail-safe contract for unconfigured state.");
      StructuredTradeDecision decision = provider.analyze(testContext);
      assertThat(decision).isNotNull();
      assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
      assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
      assertThat(decision.rejectionReason()).contains("XAI_API_KEY_NOT_CONFIGURED");
    } else {
      log.info("XAI_API_KEY is detected in environment. Executing live connectivity probe against xAI endpoint: {}", baseUrl);
      StructuredTradeDecision decision = provider.analyze(testContext);
      assertThat(decision).isNotNull();
      log.info("Live xAI/Grok response received: provider={} model={} decision={} validationStatus={}",
          decision.provider(), decision.model(), decision.decision(), decision.validationStatus());
      // Even if live response occurs, decision cannot directly trade without RiskEngine validation
      assertThat(decision.provider()).isEqualTo("XAI_GROK");
    }
  }

  @Test
  void testDirectXaiEndpointReachable_whenKeyPresent() throws Exception {
    String apiKey = System.getenv("XAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      log.info("Skipping direct HTTP probe because XAI_API_KEY is absent.");
      return;
    }

    String baseUrl = System.getenv("XAI_BASE_URL") != null ? System.getenv("XAI_BASE_URL") : "https://api.x.ai/v1";
    String endpoint = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";

    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    log.info("xAI /models HTTP status: {}", response.statusCode());
    assertThat(response.statusCode()).isIn(200, 401, 429);
  }
}
