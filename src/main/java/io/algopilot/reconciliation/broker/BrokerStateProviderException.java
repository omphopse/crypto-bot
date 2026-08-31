package io.algopilot.reconciliation.broker;

public class BrokerStateProviderException extends RuntimeException {
  public BrokerStateProviderException(String message) {
    super(message);
  }

  public BrokerStateProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
