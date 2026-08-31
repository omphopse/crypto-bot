package io.algopilot.adapter.alpaca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "algopilot.alpaca")
public class AlpacaConfig {
  private String baseUrl = "https://paper-api.alpaca.markets/v2";
  private String keyId = "";
  private String secretKey = "";

  public void validate() {
    if (baseUrl != null && baseUrl.contains("api.alpaca.markets") && !baseUrl.contains("paper-api.alpaca.markets")) {
      throw new IllegalStateException("LIVE_TRADING_DISABLED: Alpaca adapter strictly requires paper-api.alpaca.markets URL");
    }
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
    validate();
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }
}
