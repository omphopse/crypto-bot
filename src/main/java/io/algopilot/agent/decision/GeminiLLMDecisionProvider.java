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
 * Real Google Gemini LLM decision provider using the Gemini REST API.
 * Emits strictly structured trade decisions evaluated deterministically by the RiskEngine.
 * Secrets are never logged or stored in trading contexts.
 */
@Component
public class GeminiLLMDecisionProvider implements LLMDecisionProvider {
  private static final Logger log = LoggerFactory.getLogger(GeminiLLMDecisionProvider.class);

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final DecisionPromptBuilder promptBuilder;
  private final ObjectMapper json;
  private final HttpClient httpClient;
  private final Clock clock;

  @Autowired
  public GeminiLLMDecisionProvider(
      @Value("${algopilot.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
      @Value("${algopilot.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
      @Value("${algopilot.ai.gemini.model:gemini-3.6-flash}") String model,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      @Autowired(required = false) Clock clock
  ) {
    this(apiKey, baseUrl, model, promptBuilder, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), clock != null ? clock : Clock.systemUTC());
  }

  public GeminiLLMDecisionProvider(
      String apiKey,
      String baseUrl,
      String model,
      DecisionPromptBuilder promptBuilder,
      ObjectMapper json,
      HttpClient httpClient,
      Clock clock
  ) {
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://generativelanguage.googleapis.com";
    this.model = model != null && !model.isBlank() ? model.trim() : "gemini-3.6-flash";
    this.promptBuilder = promptBuilder;
    this.json = json;
    this.httpClient = httpClient;
    this.clock = clock;
  }

  @Override
  public String providerName() {
    return "GOOGLE_GEMINI";
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
      log.warn("Gemini API key is not configured. Failing safe with NO_ACTION.");
      return createSafeFallback(context, "GEMINI_API_KEY_NOT_CONFIGURED", now, expiresAt, 0L, 0, 0, BigDecimal.ZERO);
    }

    String prompt = promptBuilder.buildPrompt(context);
    long startTime = now.toEpochMilli();

    try {
      ObjectNode requestBody = json.createObjectNode();
      ArrayNode contents = requestBody.putArray("contents");
      ObjectNode content = contents.addObject();
      ArrayNode parts = content.putArray("parts");
      parts.addObject().put("text", prompt);

      ObjectNode genConfig = requestBody.putObject("generationConfig");
      genConfig.put("responseMimeType", "application/json");
      genConfig.put("temperature", 0.1);

      String requestJson = json.writeValueAsString(requestBody);

      String endpoint = String.format("%s/v1beta/models/%s:generateContent?key=%s", baseUrl, model, apiKey);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(6))
          .POST(HttpRequest.BodyPublishers.ofString(requestJson))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      long latencyMs = clock.instant().toEpochMilli() - startTime;

      if (response.statusCode() != 200) {
        log.warn("Gemini API returned non-200 status code: {} body: {}. Failing safe.", response.statusCode(), response.body());
        return createSafeFallback(context, "GEMINI_HTTP_" + response.statusCode(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      JsonNode rootNode = json.readTree(response.body());
      JsonNode candidates = rootNode.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) {
        log.warn("Gemini response contained no candidates. Failing safe.");
        return createSafeFallback(context, "GEMINI_NO_CANDIDATES", now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
      }

      String responseText = candidates.get(0).path("content").path("parts").get(0).path("text").asText().trim();
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

      int inTokens = rootNode.path("usageMetadata").path("promptTokenCount").asInt(450);
      int outTokens = rootNode.path("usageMetadata").path("candidatesTokenCount").asInt(150);

      // Pricing: $0.075 / 1M in, $0.30 / 1M out for Gemini Flash
      BigDecimal inCost = BigDecimal.valueOf(inTokens).multiply(new BigDecimal("0.000000075"));
      BigDecimal outCost = BigDecimal.valueOf(outTokens).multiply(new BigDecimal("0.000000300"));
      BigDecimal totalCost = inCost.add(outCost).setScale(6, RoundingMode.HALF_UP);

      TradeAction action = parseTradeAction(decisionJson.path("decision").asText("NO_ACTION"));
      String decisionSymbol = decisionJson.path("symbol").asText(symbol);
      BigDecimal confidence = parseBigDecimalSafely(decisionJson, "confidence", new BigDecimal("0.50"));
      BigDecimal qty = parseBigDecimalSafely(decisionJson, "quantity", BigDecimal.ZERO);
      BigDecimal sl = parseBigDecimalSafely(decisionJson, "stopLoss", BigDecimal.ZERO);
      BigDecimal tp = parseBigDecimalSafely(decisionJson, "takeProfit", BigDecimal.ZERO);
      String thesis = decisionJson.path("thesis").asText("Gemini multi-factor market analysis.");

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
      log.error("Gemini decision processing failed with exception: {}", e.getMessage(), e);
      return createSafeFallback(context, "GEMINI_EXCEPTION:" + e.getClass().getSimpleName(), now, expiresAt, latencyMs, 0, 0, BigDecimal.ZERO);
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
}
