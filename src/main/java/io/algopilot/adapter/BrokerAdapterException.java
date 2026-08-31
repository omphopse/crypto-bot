package io.algopilot.adapter;

public class BrokerAdapterException extends RuntimeException {
  public BrokerAdapterException(String message) {
    super(message);
  }

  public BrokerAdapterException(String message, Throwable cause) {
    super(message, cause);
  }
}
