package io.algopilot.reconciliation.broker;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default provider implementation when no external exchange adapter is configured.
 * Does not fabricate execution results; safely reports provider unconfigured state.
 */
@Component
@ConditionalOnMissingBean(BrokerStateProvider.class)
public class DefaultBrokerStateProvider implements BrokerStateProvider {
  @Override
  public BrokerAccountBalance fetchBalance(Broker broker, ExecutionMode mode) {
    throw new BrokerStateProviderException("BROKER_ADAPTER_NOT_CONFIGURED:" + broker + "_" + mode);
  }

  @Override
  public List<BrokerOrder> fetchOpenOrders(Broker broker, ExecutionMode mode, String botId) {
    throw new BrokerStateProviderException("BROKER_ADAPTER_NOT_CONFIGURED:" + broker + "_" + mode);
  }

  @Override
  public List<BrokerFill> fetchFills(Broker broker, ExecutionMode mode, String botId, Instant since) {
    throw new BrokerStateProviderException("BROKER_ADAPTER_NOT_CONFIGURED:" + broker + "_" + mode);
  }

  @Override
  public List<BrokerPosition> fetchPositions(Broker broker, ExecutionMode mode, String botId) {
    throw new BrokerStateProviderException("BROKER_ADAPTER_NOT_CONFIGURED:" + broker + "_" + mode);
  }
}
