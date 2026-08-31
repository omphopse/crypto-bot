package io.algopilot.adapter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.algopilot.adapter.alpaca.AlpacaPaperAdapter;
import io.algopilot.adapter.bybit.BybitDemoAdapter;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerOrder;
import io.algopilot.reconciliation.broker.BrokerStateProviderException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CompositeBrokerStateProviderTest {
  private AlpacaPaperAdapter alpacaAdapter;
  private BybitDemoAdapter bybitAdapter;
  private CompositeBrokerStateProvider composite;

  @BeforeEach
  void setUp() {
    alpacaAdapter = mock(AlpacaPaperAdapter.class);
    when(alpacaAdapter.broker()).thenReturn(Broker.ALPACA_PAPER);
    when(alpacaAdapter.supportedMode()).thenReturn(ExecutionMode.PAPER);

    bybitAdapter = mock(BybitDemoAdapter.class);
    when(bybitAdapter.broker()).thenReturn(Broker.BYBIT_DEMO);
    when(bybitAdapter.supportedMode()).thenReturn(ExecutionMode.DEMO);

    composite = new CompositeBrokerStateProvider(List.of(alpacaAdapter, bybitAdapter));
  }

  @Test
  void testFetchBalance_routesToAlpaca() {
    BrokerAccountBalance balance = new BrokerAccountBalance("USD", new BigDecimal("10000"), new BigDecimal("20000"), new BigDecimal("15000"), Instant.now());
    when(alpacaAdapter.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.PAPER)).thenReturn(balance);

    BrokerAccountBalance result = composite.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.PAPER);
    assertEquals(balance, result);
    verify(alpacaAdapter).fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.PAPER);
    verify(bybitAdapter, never()).fetchBalance(any(), any());
  }

  @Test
  void testFetchOpenOrders_routesToBybit() {
    List<BrokerOrder> orders = List.of();
    when(bybitAdapter.fetchOpenOrders(Broker.BYBIT_DEMO, ExecutionMode.DEMO, "bot-1")).thenReturn(orders);

    List<BrokerOrder> result = composite.fetchOpenOrders(Broker.BYBIT_DEMO, ExecutionMode.DEMO, "bot-1");
    assertEquals(orders, result);
    verify(bybitAdapter).fetchOpenOrders(Broker.BYBIT_DEMO, ExecutionMode.DEMO, "bot-1");
  }

  @Test
  void testLiveTrading_strictlyRejected() {
    BrokerStateProviderException ex = assertThrows(BrokerStateProviderException.class, () -> composite.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.LIVE));
    assertEquals("LIVE_TRADING_DISABLED", ex.getMessage());
  }

  @Test
  void testUnsupportedBroker_throwsException() {
    assertThrows(BrokerStateProviderException.class, () -> composite.fetchBalance(Broker.ALPACA_PAPER, ExecutionMode.DEMO));
  }
}
