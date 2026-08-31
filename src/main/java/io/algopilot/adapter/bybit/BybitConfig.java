package io.algopilot.adapter.bybit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "algopilot.bybit")
public class BybitConfig {
  private String baseUrl = "https://api-demo.bybit.com";
  private String apiKey = "";
  private String apiSecret = "";
  private String recvWindow = "5000";

  public BybitConfig() {}

  public BybitConfig(String apiKey, String apiSecret, String baseUrl) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.baseUrl = baseUrl;
    validate();
  }

  public void validate() {
    if (baseUrl != null && baseUrl.contains("api.bybit.com") && !baseUrl.contains("api-demo.bybit.com") && !baseUrl.contains("api-testnet.bybit.com")) {
      throw new IllegalStateException("LIVE_TRADING_DISABLED: Bybit adapter strictly requires api-demo.bybit.com URL");
    }
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    validate();
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public String getRecvWindow() {
    return recvWindow;
  }

  public void setRecvWindow(String recvWindow) {
    this.recvWindow = recvWindow;
  }
}
