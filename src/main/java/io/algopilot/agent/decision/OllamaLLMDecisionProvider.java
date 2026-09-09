package io.algopilot.agent.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.agent.context.TradingContext;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local Ollama LLM decision provider connecting to Ollama's local chat endpoint.
 * Emits strictly structured trade decisions evaluated deterministically by the RiskEngine.
 * The provider has ZERO direct execution authority.
 */
@Component
public class OllamaLLMDecisionProvider implements LLMDecisionProvider {
  private static final Logger log = LoggerFactory.getLogger(OllamaLLMDecisionProvider.class);

  private final String baseUrl;
  private final String model;
  private final int timeoutSeconds;
  private final DecisionPromptBuilder promptBuilder;
  private final ObjectMapper json;
  private final HttpClient httpClient;
  private final Clock clock;

  @Autowired
  public OllamaLLMDecisionProvider(
      @Value("${algopilot.ai.ollama.base-url:${OLLAMA_BASE_URL:http://127.0.0.1:11434}}") String baseUrl,
      @Value("${algopilot.ai.ollama.model:${OLLAMA_MODEL:gemma3:4b}}") String model,
      @Value("${algopilot.ai.ollama.timeout-seconds:${OLLAMA_TIMEOUT_SECONDS:45}}") int timeoutSeconds,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      @Autowired(required = false) Clock clock
  ) {
    this(
        baseUrl,
        model,
        timeoutSeconds,
        promptBuilder,
        json,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10))).build(),
        clock != null ? clock : Clock.systemUTC()
    );
  }

  public OllamaLLMDecisionProvider(
      String baseUrl,
      String model,
      int timeoutSeconds,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      HttpClient httpClient,
      Clock clock
  ) {
    this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "http://127.0.0.1:11434";
    this.model = model != null && !model.isBlank() ? model.trim() : "gemma3:4b";
    this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 45;
    this.promptBuilder = promptBuilder;
    this.json = json;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  @Override
  public String providerName() {
    return "ollama";
  }

  @Override
  public String modelName() {
    return this.model;
  }

  @Override
  public StructuredTradeDecision analyze(TradingContext context) {
    Instant now = clock.instant();
    Instant expiresAt = now.plusSeconds(300);

    String symbol = context.market() != null ? context.market().symbol() : "UNKNOWN";
    BigDecimal price = context.market() != null ? context.market().lastPrice() : BigDecimal.ZERO;
    UUID stratVerId = context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID();

    String prompt = promptBuilder.buildPrompt(context);
    long startTime = now.toEpochMilli();

    try {
      ObjectNode requestBody = json.createObjectNode();
      requestBody.put("model", model);
      requestBody.put("stream", false);
      requestBody.put("format", "json");

      ObjectNode options = requestBody.putObject("options");
      options.put("temperature", 0.1);

      ArrayNode messages = requestBody.putArray("messages");
      ObjectNode userMessage = messages.addObject();
      userMessage.put("role", "user");
      userMessage.put("content", prompt);

      String requestJson = json.writeValueAsString(requestBody);
      String endpoint = baseUrl.endsWith("/") ? baseUrl + "api/chat" : baseUrl + "/api/chat";

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(timeoutSeconds))
          .POST(HttpRequest.BodyPublishers.ofString(requestJson))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      long latencyMs = clock.instant().toEpochMilli() - startTime;

      if (response.statusCode() != 200) {
        log.warn("Ollama API returned non-200 status code: {} body: {}. Failing safe.", response.statusCode(), response.body());
        return createSafeFallback(context, "OLLAMA_HTTP_" + response.statusCode(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      JsonNode rootNode = json.readTree(response.body());
      JsonNode messageNode = rootNode.path("message");
      String responseText = messageNode.path("content").asText("").trim();

      if (responseText.isBlank()) {
        log.warn("Ollama response contained empty content. Failing safe.");
        return createSafeFallback(context, "OLLAMA_EMPTY_RESPONSE", now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      if (responseText.startsWith("```json")) {
        responseText = responseText.substring(7);
      } else if (responseText.startsWith("```")) {
        responseText = responseText.substring(3);
      }
      if (responseText.endsWith("```")) {
        responseText = responseText.substring(0, responseText.length() - 3);
      }
      responseText = responseText.trim();

      JsonNode decisionJson = json.readTree(responseText);

      int inTokens = rootNode.path("prompt_eval_count").asInt(350);
      int outTokens = rootNode.path("eval_count").asInt(120);
      BigDecimal totalCost = BigDecimal.ZERO; // Local model execution carries zero direct API cost

      TradeAction action = parseTradeAction(decisionJson.path("decision").asText("NO_ACTION"));
      String decisionSymbol = decisionJson.path("symbol").asText(symbol);
      BigDecimal confidence = parseBigDecimalSafely(decisionJson, "confidence", new BigDecimal("0.50"));
      BigDecimal qty = parseBigDecimalSafely(decisionJson, "quantity", BigDecimal.ZERO);
      BigDecimal sl = parseBigDecimalSafely(decisionJson, "stopLoss", BigDecimal.ZERO);
      BigDecimal tp = parseBigDecimalSafely(decisionJson, "takeProfit", BigDecimal.ZERO);
      String thesis = decisionJson.path("thesis").asText("Ollama " + model + " multi-factor market analysis.");

      List<UUID> evidenceRefs = new ArrayList<>();
      if (decisionJson.has("evidenceReferences") && decisionJson.path("evidenceReferences").isArray()) {
        for (JsonNode ref : decisionJson.path("evidenceReferences")) {
          try {
            evidenceRefs.add(UUID.fromString(ref.asText()));
          } catch (Exception ignored) {}
        }
      }

      List<String> riskFactors = new ArrayList<>();
      if (decisionJson.has("riskFactors") && decisionJson.path("riskFactors").isArray()) {
        for (JsonNode rf : decisionJson.path("riskFactors")) riskFactors.add(rf.asText());
      }

      List<String> invalidation = new ArrayList<>();
      if (decisionJson.has("invalidationConditions") && decisionJson.path("invalidationConditions").isArray()) {
        for (JsonNode ic : decisionJson.path("invalidationConditions")) invalidation.add(ic.asText());
      }

      String side = action == TradeAction.BUY ? "BUY" : (action == TradeAction.SELL || action == TradeAction.REDUCE ? "SELL" : "FLAT");

      return new StructuredTradeDecision(
          UUID.randomUUID(),
          context.contextId(),
          context.contextHash(),
          context.botId(),
          context.agentSessionId(),
          stratVerId,
          providerName(),
          modelName(),
          action,
          decisionSymbol,
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
          latencyMs,
          inTokens,
          outTokens,
          totalCost,
          now,
          expiresAt
      );

    } catch (HttpTimeoutException e) {
      long latencyMs = clock.instant().toEpochMilli() - startTime;
      log.warn("Ollama decision processing timed out after {}s: {}", timeoutSeconds, e.getMessage());
      return createSafeFallback(context, "OLLAMA_TIMEOUT", now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
    } catch (Exception e) {
      long latencyMs = clock.instant().toEpochMilli() - startTime;
      log.error("Ollama decision processing failed with exception: {}", e.getMessage(), e);
      return createSafeFallback(context, "OLLAMA_EXCEPTION:" + e.getClass().getSimpleName(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
    }
  }

  private TradeAction parseTradeAction(String raw) {
    if (raw == null) return TradeAction.NO_ACTION;
    try {
      return TradeAction.valueOf(raw.trim().toUpperCase());
    } catch (Exception e) {
      return TradeAction.NO_ACTION;
    }
  }

  private BigDecimal parseBigDecimalSafely(JsonNode node, String fieldName, BigDecimal defaultValue) {
    if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
      return defaultValue;
    }
    try {
      String text = node.get(fieldName).asText().trim();
      if (text.isEmpty() || "null".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text) || "n/a".equalsIgnoreCase(text)) {
        return defaultValue;
      }
      return new BigDecimal(text);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private StructuredTradeDecision createSafeFallback(
      TradingContext context,
      String reason,
      Instant now,
      Instant expiresAt,
      long latencyMs,
      int inTokens,
      int outTokens,
      BigDecimal cost
  ) {
    String symbol = context.market() != null ? context.market().symbol() : "UNKNOWN";
    BigDecimal price = context.market() != null ? context.market().lastPrice() : BigDecimal.ZERO;
    UUID stratVerId = context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID();

    return new StructuredTradeDecision(
        UUID.randomUUID(),
        context.contextId(),
        context.contextHash(),
        context.botId(),
        context.agentSessionId(),
        stratVerId,
        providerName(),
        modelName(),
        TradeAction.NO_ACTION,
        symbol,
        "FLAT",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        price,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "INTRADAY",
        "Safe fallback decision: " + reason,
        List.of(),
        List.of("Provider unavailable or unconfigured"),
        List.of("Automatic fail-safe"),
        ValidationStatus.FAILED,
        reason,
        latencyMs,
        inTokens,
        outTokens,
        cost,
        now,
        expiresAt
    );
  }
}
