package io.algopilot.adapter;

import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerFill;
import io.algopilot.reconciliation.broker.BrokerOrder;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateProvider;
import io.algopilot.reconciliation.broker.BrokerStateProviderException;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CompositeBrokerStateProvider implements BrokerStateProvider {
  private final List<BrokerStateProvider> providers;

  public CompositeBrokerStateProvider(List<BrokerStateProvider> providers) {
    this.providers = providers;
  }

  @Override
  public BrokerAccountBalance fetchBalance(Broker broker, ExecutionMode mode) {
    return resolve(broker, mode).fetchBalance(broker, mode);
  }

  @Override
  public List<BrokerOrder> fetchOpenOrders(Broker broker, ExecutionMode mode, String botId) {
    return resolve(broker, mode).fetchOpenOrders(broker, mode, botId);
  }

  @Override
  public List<BrokerFill> fetchFills(Broker broker, ExecutionMode mode, String botId, Instant since) {
    return resolve(broker, mode).fetchFills(broker, mode, botId, since);
  }

  @Override
  public List<BrokerPosition> fetchPositions(Broker broker, ExecutionMode mode, String botId) {
    return resolve(broker, mode).fetchPositions(broker, mode, botId);
  }

  @Override
  public BrokerStateSnapshot fetchSnapshot(Broker broker, ExecutionMode mode, String botId) {
    return resolve(broker, mode).fetchSnapshot(broker, mode, botId);
  }

  private BrokerStateProvider resolve(Broker broker, ExecutionMode mode) {
    if (mode == ExecutionMode.LIVE) {
      throw new BrokerStateProviderException("LIVE_TRADING_DISABLED");
    }

    for (BrokerStateProvider provider : providers) {
      if (provider instanceof BrokerOrderAdapter adapter) {
        if (adapter.broker() == broker && adapter.supportedMode() == mode) {
          return provider;
        }
      }
    }
    throw new BrokerStateProviderException("NO_ADAPTER_FOR_BROKER:" + broker + "_" + mode);
  }
}
