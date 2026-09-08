package io.algopilot.agent.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.algopilot.agent.context.TradingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Real xAI / Grok LLM decision provider using the xAI OpenAI-compatible REST API.
 * Emits strictly structured trade decisions evaluated deterministically by the RiskEngine.
 * Secrets (XAI_API_KEY) are never logged, persisted, or stored in trading contexts.
 */
@Component
public class GrokLLMDecisionProvider implements LLMDecisionProvider {
  private static final Logger log = LoggerFactory.getLogger(GrokLLMDecisionProvider.class);

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final DecisionPromptBuilder promptBuilder;
  private final ObjectMapper json;
  private final HttpClient httpClient;
  private final Clock clock;

  @Autowired
  public GrokLLMDecisionProvider(
      @Value("${algopilot.ai.xai.api-key:${XAI_API_KEY:}}") String apiKey,
      @Value("${algopilot.ai.xai.base-url:https://api.x.ai/v1}") String baseUrl,
      @Value("${algopilot.ai.xai.model:grok-2-latest}") String model,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      @Autowired(required = false) Clock clock
  ) {
    this(apiKey, baseUrl, model, promptBuilder, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), clock != null ? clock : Clock.systemUTC());
  }

  public GrokLLMDecisionProvider(
      String apiKey,
      String baseUrl,
      String model,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      HttpClient httpClient,
      Clock clock
  ) {
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.x.ai/v1";
    this.model = model != null && !model.isBlank() ? model.trim() : "grok-2-latest";
    this.promptBuilder = promptBuilder;
    this.json = json;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  @Override
  public String providerName() {
    return "XAI_GROK";
  }

  @Override
  public String modelName() {
    return this.model;
  }

  public boolean isApiKeyConfigured() {
    return !apiKey.isBlank();
  }

  @Override
  public StructuredTradeDecision analyze(TradingContext context) {
    Instant now = clock.instant();
    Instant expiresAt = now.plusSeconds(300);

    String symbol = context.market() != null ? context.market().symbol() : "UNKNOWN";
    BigDecimal price = context.market() != null ? context.market().lastPrice() : BigDecimal.ZERO;
    UUID stratVerId = context.strategy() != null ? context.strategy().strategyVersionId() : UUID.randomUUID();

    if (!isApiKeyConfigured()) {
      log.warn("xAI/Grok API key is not configured. Failing safe with NO_ACTION.");
      return createSafeFallback(context, "XAI_API_KEY_NOT_CONFIGURED", now, expiresAt, 0L, 0, 0, BigDecimal.ZERO);
    }

    String prompt = promptBuilder.buildPrompt(context);
    long startTime = now.toEpochMilli();

    try {
      ObjectNode requestBody = json.createObjectNode();
      requestBody.put("model", model);
      requestBody.put("temperature", 0.1);

      ObjectNode responseFormat = requestBody.putObject("response_format");
      responseFormat.put("type", "json_object");

      ArrayNode messages = requestBody.putArray("messages");
      ObjectNode userMessage = messages.addObject();
      userMessage.put("role", "user");
      userMessage.put("content", prompt);

      String requestJson = json.writeValueAsString(requestBody);
      String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .timeout(Duration.ofSeconds(6))
          .POST(HttpRequest.BodyPublishers.ofString(requestJson))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      long latencyMs = clock.instant().toEpochMilli() - startTime;

      if (response.statusCode() != 200) {
        log.warn("xAI/Grok API returned non-200 status code: {} body: {}. Failing safe.", response.statusCode(), response.body());
        return createSafeFallback(context, "XAI_HTTP_" + response.statusCode(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      JsonNode rootNode = json.readTree(response.body());
      JsonNode choices = rootNode.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        log.warn("xAI/Grok response contained no choices. Failing safe.");
        return createSafeFallback(context, "XAI_NO_CHOICES", now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      String responseText = choices.get(0).path("message").path("content").asText().trim();
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

      int inTokens = rootNode.path("usage").path("prompt_tokens").asInt(450);
      int outTokens = rootNode.path("usage").path("completion_tokens").asInt(150);

      // Pricing for Grok-2: $2.00 / 1M in, $10.00 / 1M out
      BigDecimal inCost = BigDecimal.valueOf(inTokens).multiply(new BigDecimal("0.00000200"));
      BigDecimal outCost = BigDecimal.valueOf(outTokens).multiply(new BigDecimal("0.00001000"));
      BigDecimal totalCost = inCost.add(outCost).setScale(6, RoundingMode.HALF_UP);

      TradeAction action = parseTradeAction(decisionJson.path("decision").asText("NO_ACTION"));
      String decisionSymbol = decisionJson.path("symbol").asText(symbol);
      BigDecimal confidence = parseBigDecimalSafely(decisionJson, "confidence", new BigDecimal("0.50"));
      BigDecimal qty = parseBigDecimalSafely(decisionJson, "quantity", BigDecimal.ZERO);
      BigDecimal sl = parseBigDecimalSafely(decisionJson, "stopLoss", BigDecimal.ZERO);
      BigDecimal tp = parseBigDecimalSafely(decisionJson, "takeProfit", BigDecimal.ZERO);
      String thesis = decisionJson.path("thesis").asText("xAI Grok multi-factor market analysis.");

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

    } catch (Exception e) {
      long latencyMs = clock.instant().toEpochMilli() - startTime;
      log.error("xAI/Grok decision processing failed with exception: {}", e.getMessage(), e);
      return createSafeFallback(context, "XAI_EXCEPTION:" + e.getClass().getSimpleName(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
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
