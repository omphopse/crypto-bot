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
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrokLLMDecisionProviderTest {
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
    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("XAI_API_KEY_NOT_CONFIGURED");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenValidJsonResponse_parsesCorrectly() throws IOException, InterruptedException {
    String grokResponseBody = """
        {
          "id": "chatcmpl-test-123",
          "object": "chat.completion",
          "created": 1725800000,
          "model": "grok-2-latest",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "{\\"decision\\":\\"BUY\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.88,\\"quantity\\":0.15,\\"stopLoss\\":59750.00,\\"takeProfit\\":60500.00,\\"thesis\\":\\"Bullish breakout confirmed by Grok reasoning.\\",\\"riskFactors\\":[\\"volatility\\"],\\"invalidationConditions\\":[\\"support break\\"]}"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 460,
            "completion_tokens": 140,
            "total_tokens": 600
          }
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(grokResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.provider()).isEqualTo("XAI_GROK");
    assertThat(decision.model()).isEqualTo("grok-2-latest");
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.symbol()).isEqualTo("BTC/USD");
    assertThat(decision.confidence()).isEqualByComparingTo("0.88");
    assertThat(decision.quantity()).isEqualByComparingTo("0.15");
    assertThat(decision.stopLoss()).isEqualByComparingTo("59750.00");
    assertThat(decision.takeProfit()).isEqualByComparingTo("60500.00");
    assertThat(decision.thesis()).isEqualTo("Bullish breakout confirmed by Grok reasoning.");
    assertThat(decision.inputTokens()).isEqualTo(460);
    assertThat(decision.outputTokens()).isEqualTo(140);
    assertThat(decision.estimatedCostUsd()).isGreaterThan(BigDecimal.ZERO);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenMarkdownFencedJsonResponse_parsesCorrectly() throws IOException, InterruptedException {
    String grokResponseBody = """
        {
          "id": "chatcmpl-test-456",
          "object": "chat.completion",
          "created": 1725800000,
          "model": "grok-2-latest",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "```json\\n{\\"decision\\":\\"NO_ACTION\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.50,\\"stopLoss\\":\\"none\\",\\"takeProfit\\":\\"none\\",\\"thesis\\":\\"Ranging market.\\"}\\n```"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 400,
            "completion_tokens": 80,
            "total_tokens": 480
          }
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(grokResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.stopLoss()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(decision.takeProfit()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(decision.thesis()).isEqualTo("Ranging market.");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttp401Unauthorized_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(401);
    when(mockResponse.body()).thenReturn("{\"error\": \"Incorrect API key provided\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("XAI_HTTP_401");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttp429RateLimit_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(429);
    when(mockResponse.body()).thenReturn("{\"error\": \"Rate limit exceeded\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("XAI_HTTP_429");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttp500ServerError_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn("{\"error\": \"Internal Server Error\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("XAI_HTTP_500");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenTimeoutOccurs_failsSafe() throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("Request timed out after 6000ms"));

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("XAI_EXCEPTION:HttpTimeoutException");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenMalformedJsonResponse_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("{\"choices\": [{\"message\": {\"content\": \"NOT_VALID_JSON\"}}]}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("XAI_EXCEPTION");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenEmptyChoices_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("{\"choices\": []}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    GrokLLMDecisionProvider provider = new GrokLLMDecisionProvider("dummy_key", "https://api.x.ai/v1", "grok-2-latest", promptBuilder, json, httpClient, clock);

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("XAI_NO_CHOICES");
  }

  @Test
  void testProviderDelegationLogic() {
    GeminiLLMDecisionProvider geminiMock = mock(GeminiLLMDecisionProvider.class);
    GrokLLMDecisionProvider grokMock = mock(GrokLLMDecisionProvider.class);
    FakeLLMDecisionProvider fakeMock = mock(FakeLLMDecisionProvider.class);

    when(geminiMock.isApiKeyConfigured()).thenReturn(true);
    when(grokMock.isApiKeyConfigured()).thenReturn(true);

    // 1. xai mode with key -> delegates to grok
    DelegatingLLMDecisionProvider delegatingXai = new DelegatingLLMDecisionProvider(geminiMock, grokMock, fakeMock, "xai");
    assertThat(delegatingXai.getActiveDelegate()).isSameAs(grokMock);

    // 2. grok mode with key -> delegates to grok
    DelegatingLLMDecisionProvider delegatingGrok = new DelegatingLLMDecisionProvider(geminiMock, grokMock, fakeMock, "grok");
    assertThat(delegatingGrok.getActiveDelegate()).isSameAs(grokMock);

    // 3. grok mode without key -> falls back to fake
    when(grokMock.isApiKeyConfigured()).thenReturn(false);
    DelegatingLLMDecisionProvider delegatingGrokNoKey = new DelegatingLLMDecisionProvider(geminiMock, grokMock, fakeMock, "grok");
    assertThat(delegatingGrokNoKey.getActiveDelegate()).isSameAs(fakeMock);

    // 4. gemini mode with key -> delegates to gemini
    DelegatingLLMDecisionProvider delegatingGemini = new DelegatingLLMDecisionProvider(geminiMock, grokMock, fakeMock, "gemini");
    assertThat(delegatingGemini.getActiveDelegate()).isSameAs(geminiMock);

    // 5. fake mode -> delegates to fake
    DelegatingLLMDecisionProvider delegatingFake = new DelegatingLLMDecisionProvider(geminiMock, grokMock, fakeMock, "fake");
    assertThat(delegatingFake.getActiveDelegate()).isSameAs(fakeMock);
  }
}
