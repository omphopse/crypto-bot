package io.algopilot.agent.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.mockito.ArgumentCaptor;

class OllamaLLMDecisionProviderTest {
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

    context = new TradingContext(
        UUID.randomUUID(), "hash123", clock.instant(), botId, sessionId, "ALPACA_PAPER", "PAPER",
        AgentState.SCANNING, AutonomousMode.PAPER_AUTONOMOUS, market, null, null, strategy, null,
        List.of(), List.of(), null, null, List.of(), null, null, null
    );
  }

  @Test
  void testProviderCreationAndMetadata() {
    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    assertThat(provider.providerName()).isEqualTo("ollama");
    assertThat(provider.modelName()).isEqualTo("gemma3:4b");
  }

  @Test
  void testCorrectModelSelection() {
    OllamaLLMDecisionProvider customModelProvider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "custom-model:latest", 30, promptBuilder, json, httpClient, clock
    );

    assertThat(customModelProvider.providerName()).isEqualTo("ollama");
    assertThat(customModelProvider.modelName()).isEqualTo("custom-model:latest");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRequestPayloadAndStructuredOutputConfig() throws IOException, InterruptedException {
    String ollamaResponseBody = """
        {
          "model": "gemma3:4b",
          "created_at": "2026-09-09T18:00:00Z",
          "message": {
            "role": "assistant",
            "content": "{\\"decision\\":\\"HOLD\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.60,\\"quantity\\":0.0,\\"stopLoss\\":0.0,\\"takeProfit\\":0.0,\\"thesis\\":\\"Market consolidating.\\"}"
          },
          "done": true,
          "prompt_eval_count": 350,
          "eval_count": 80
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(ollamaResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    provider.analyze(context);

    ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest sentRequest = requestCaptor.getValue();
    assertThat(sentRequest.uri().toString()).isEqualTo("http://127.0.0.1:11434/api/chat");
    assertThat(sentRequest.headers().firstValue("Content-Type")).contains("application/json");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSuccessfulValidDecisionParsing() throws IOException, InterruptedException {
    String ollamaResponseBody = """
        {
          "model": "gemma3:4b",
          "created_at": "2026-09-09T18:00:00Z",
          "message": {
            "role": "assistant",
            "content": "{\\"decision\\":\\"BUY\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.85,\\"quantity\\":0.12,\\"stopLoss\\":59500.00,\\"takeProfit\\":61200.00,\\"thesis\\":\\"Bullish breakout on volume.\\",\\"riskFactors\\":[\\"volatility\\"],\\"invalidationConditions\\":[\\"support break\\"]}"
          },
          "done": true,
          "prompt_eval_count": 420,
          "eval_count": 110
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(ollamaResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.provider()).isEqualTo("ollama");
    assertThat(decision.model()).isEqualTo("gemma3:4b");
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.symbol()).isEqualTo("BTC/USD");
    assertThat(decision.side()).isEqualTo("BUY");
    assertThat(decision.confidence()).isEqualByComparingTo("0.85");
    assertThat(decision.quantity()).isEqualByComparingTo("0.12");
    assertThat(decision.stopLoss()).isEqualByComparingTo("59500.00");
    assertThat(decision.takeProfit()).isEqualByComparingTo("61200.00");
    assertThat(decision.thesis()).contains("Bullish breakout");
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
    assertThat(decision.estimatedCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(decision.inputTokens()).isEqualTo(420);
    assertThat(decision.outputTokens()).isEqualTo(110);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testMarkdownFencedJsonResponse_parsesCorrectly() throws IOException, InterruptedException {
    String ollamaResponseBody = """
        {
          "model": "gemma3:4b",
          "message": {
            "role": "assistant",
            "content": "```json\\n{\\"decision\\":\\"SELL\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.78,\\"quantity\\":0.05,\\"stopLoss\\":60500.00,\\"takeProfit\\":59000.00,\\"thesis\\":\\"Bearish divergence.\\"}\\n```"
          },
          "done": true
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(ollamaResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.SELL);
    assertThat(decision.side()).isEqualTo("SELL");
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttp500ServerError_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(500);
    when(mockResponse.body()).thenReturn("{\"error\": \"Internal server error\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("OLLAMA_HTTP_500");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenHttp404NotFound_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(404);
    when(mockResponse.body()).thenReturn("{\"error\": \"model 'gemma3:4b' not found\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("OLLAMA_HTTP_404");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenTimeoutOccurs_failsSafe() throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("request timed out"));

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("OLLAMA_TIMEOUT");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenMalformedJsonResponse_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("{\"model\": \"gemma3:4b\", \"message\": {\"content\": \"Not valid JSON {[\"}}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("OLLAMA_EXCEPTION");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenEmptyResponse_failsSafe() throws IOException, InterruptedException {
    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn("{\"model\": \"gemma3:4b\", \"message\": {\"content\": \"\"}}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).isEqualTo("OLLAMA_EMPTY_RESPONSE");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void testWhenOllamaUnavailable_failsSafe() throws IOException, InterruptedException {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("Connection refused: connect"));

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.NO_ACTION);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(decision.rejectionReason()).contains("OLLAMA_EXCEPTION");
    assertThat(decision.isActionable()).isFalse();
  }

  @Test
  void testProviderDelegationLogic() {
    GeminiLLMDecisionProvider geminiMock = mock(GeminiLLMDecisionProvider.class);
    GrokLLMDecisionProvider grokMock = mock(GrokLLMDecisionProvider.class);
    OllamaLLMDecisionProvider ollamaMock = mock(OllamaLLMDecisionProvider.class);
    FakeLLMDecisionProvider fakeMock = mock(FakeLLMDecisionProvider.class);

    when(geminiMock.isApiKeyConfigured()).thenReturn(true);
    when(grokMock.isApiKeyConfigured()).thenReturn(true);

    // 1. ollama mode -> delegates to ollama
    DelegatingLLMDecisionProvider delegatingOllama = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, ollamaMock, fakeMock, "ollama"
    );
    assertThat(delegatingOllama.getActiveDelegate()).isSameAs(ollamaMock);

    // 2. ollama mode without bean -> falls back to fake
    DelegatingLLMDecisionProvider delegatingOllamaNull = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, null, fakeMock, "ollama"
    );
    assertThat(delegatingOllamaNull.getActiveDelegate()).isSameAs(fakeMock);

    // 3. gemini mode with key -> delegates to gemini
    DelegatingLLMDecisionProvider delegatingGemini = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, ollamaMock, fakeMock, "gemini"
    );
    assertThat(delegatingGemini.getActiveDelegate()).isSameAs(geminiMock);

    // 4. xai mode with key -> delegates to grok
    DelegatingLLMDecisionProvider delegatingXai = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, ollamaMock, fakeMock, "xai"
    );
    assertThat(delegatingXai.getActiveDelegate()).isSameAs(grokMock);

    // 5. grok mode with key -> delegates to grok
    DelegatingLLMDecisionProvider delegatingGrok = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, ollamaMock, fakeMock, "grok"
    );
    assertThat(delegatingGrok.getActiveDelegate()).isSameAs(grokMock);

    // 6. fake mode -> delegates to fake
    DelegatingLLMDecisionProvider delegatingFake = new DelegatingLLMDecisionProvider(
        geminiMock, grokMock, ollamaMock, fakeMock, "fake"
    );
    assertThat(delegatingFake.getActiveDelegate()).isSameAs(fakeMock);
  }

  @Test
  void testNoExecutionAuthority() {
    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    // Verify provider strictly adheres to LLMDecisionProvider interface and has no order dispatching methods
    assertThat(provider).isInstanceOf(LLMDecisionProvider.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testOllamaZeroQuantityBuyDecision_normalizesToNominalQuantity() throws IOException, InterruptedException {
    // Regression test for the 152/328 malformed decision issue where Ollama omitted or set quantity to 0
    String ollamaResponseBody = """
        {
          "model": "gemma3:4b",
          "message": {
            "role": "assistant",
            "content": "{\\"decision\\":\\"BUY\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":0.80,\\"quantity\\":0.0,\\"stopLoss\\":59000.0,\\"takeProfit\\":62000.0,\\"thesis\\":\\"RSI oversold rebound.\\"}"
          },
          "done": true
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(ollamaResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testOllamaPercentageStopLossAndTakeProfit_normalizesCorrectly() throws IOException, InterruptedException {
    // Regression test for cases where model emits percentage deltas (e.g. tp=0.04, sl=-0.02) and percentage confidence (85)
    String ollamaResponseBody = """
        {
          "model": "gemma3:4b",
          "message": {
            "role": "assistant",
            "content": "{\\"decision\\":\\"BUY\\",\\"symbol\\":\\"BTC/USD\\",\\"confidence\\":85,\\"quantity\\":0.0,\\"stopLoss\\":-0.02,\\"takeProfit\\":0.04,\\"thesis\\":\\"Mean Reversion.\\"}"
          },
          "done": true
        }
        """;

    HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(ollamaResponseBody);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    OllamaLLMDecisionProvider provider = new OllamaLLMDecisionProvider(
        "http://127.0.0.1:11434", "gemma3:4b", 45, promptBuilder, json, httpClient, clock
    );

    StructuredTradeDecision decision = provider.analyze(context);

    assertThat(decision).isNotNull();
    assertThat(decision.decision()).isEqualTo(TradeAction.BUY);
    assertThat(decision.confidence()).isEqualByComparingTo("0.85");
    assertThat(decision.quantity()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(decision.takeProfit()).isEqualByComparingTo("62400.00");
    assertThat(decision.stopLoss()).isEqualByComparingTo("58800.00");
    assertThat(decision.validationStatus()).isEqualTo(ValidationStatus.VALIDATED);
  }
}
