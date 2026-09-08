package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.context.FreshnessStatus;
import io.algopilot.agent.context.MarketContext;
import io.algopilot.agent.context.StrategyContext;
import io.algopilot.agent.context.TradingContext;
import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeminiLLMDecisionProviderTest {
  private DecisionPromptBuilder promptBuilder;
  private ObjectMapper json;
  private HttpClient httpClient;
  private Clock clock;
  private TradingContext context;

  @BeforeEach
  void setUp() {
    promptBuilder = new DecisionPromptBuilder();
    json = new ObjectMapper();
    httpClient = mock(HttpClient.class);
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

    context = new TradingContext(
        UUID.randomUUID(), "hash123", clock.instant(), botId, sessionId, "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market, null, null, strategy, null,
        List.of(), List.of(), null, null, List.of(), null, null, null
    );
  }

  @Test
  void testWhenApiKeyNotConfigured_failsSafeWithNoAction() {
    GeminiLLMDecisionProvider provider = new GeminiLLMDecisionProvider("", "https://generativelanguage.googleapis.com", "gemini-2.5-flash", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("GEMINI_API_KEY_NOT_CONFIGURED");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttpErrorOccurs_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(429);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GeminiLLMDecisionProvider provider = new GeminiLLMDecisionProvider("dummy_key", "https://generativelanguage.googleapis.com", "gemini-2.5-flash", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("GEMINI_HTTP_429");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenValidJsonResponse_parsesCorrectly() throws IOException, InterruptedException {
    String geminiResponseBody = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\\"decision\\":\\"BUY\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.85,\\"quantity\\":0.10,\\"stopLoss\\":59800.00,\\"takeProfit\\":60400.00,\\"thesis\\":\\"Bullish momentum observed.\\",\\"riskFactors\\":[\\"volatility\\"],\\"invalidationConditions\\":[\\"break below support\\"]}"
                  }
                ]
              }
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 420,
            "candidatesTokenCount": 130
          }
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(geminiResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GeminiLLMDecisionProvider provider = new GeminiLLMDecisionProvider("dummy_key", "https://generativelanguage.googleapis.com", "gemini-2.5-flash", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.symbol()).isEqualTo("BTC/USD");
    assertThat(decision.confidence()).isEqualByComparingTo("0.85");
    assertThat(decision.quantity()).isEqualByComparingTo("0.10");
    assertThat(decision.stopLoss()).isEqualByComparingTo("59800.00");
    assertThat(decision.takeProfit()).isEqualByComparingTo("60400.00");
    assertThat(decision.thesis()).isEqualTo("Bullish momentum observed.");
  }
}
