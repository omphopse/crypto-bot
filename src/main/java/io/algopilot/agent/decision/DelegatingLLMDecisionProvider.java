package io.algopilot.agent.decision;

import io.algopilot.agent.context.TradingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary routing decision provider that delegates to Google Gemini or the deterministic Fake provider
 * based on configuration and API key availability.
 */
@Component
@Primary
public class DelegatingLLMDecisionProvider implements LLMDecisionProvider {
  private static final Logger log = LoggerFactory.getLogger(DelegatingLLMDecisionProvider.class);

  private final GeminiLLMDecisionProvider geminiProvider;
  private final FakeLLMDecisionProvider fakeProvider;
  private final String providerMode;

  @Autowired
  public DelegatingLLMDecisionProvider(
      GeminiLLMDecisionProvider geminiProvider,
      FakeLLMDecisionProvider fakeProvider,
      @Value("${algopilot.ai.provider:gemini}") String providerMode
  ) {
    this.geminiProvider = geminiProvider;
    this.fakeProvider = fakeProvider;
    this.providerMode = providerMode != null ? providerMode.trim().toLowerCase() : "gemini";
  }

  @Override
  public String providerName() {
    return getActiveDelegate().providerName();
  }

  @Override
  public String modelName() {
    return getActiveDelegate().modelName();
  }

  @Override
  public StructuredTradeDecision analyze(TradingContext context) {
    return getActiveDelegate().analyze(context);
  }

  public LLMDecisionProvider getActiveDelegate() {
    if ("gemini".equalsIgnoreCase(providerMode) && geminiProvider.isApiKeyConfigured()) {
      return geminiProvider;
    }
    if ("gemini".equalsIgnoreCase(providerMode) && !geminiProvider.isApiKeyConfigured()) {
      log.debug("Gemini provider selected but GEMINI_API_KEY is not set. Falling back to deterministic Fake provider for testing.");
    }
    return fakeProvider;
  }
}
