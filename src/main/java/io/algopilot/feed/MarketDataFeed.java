package io.algopilot.feed;

import io.algopilot.bot.Broker;
import java.util.Set;

public interface MarketDataFeed {
  Broker getBroker();

  void subscribe(String symbol);

  void unsubscribe(String symbol);

  Set<String> getSubscribedSymbols();

  void start();

  void stop();

  boolean isConnected();
}
