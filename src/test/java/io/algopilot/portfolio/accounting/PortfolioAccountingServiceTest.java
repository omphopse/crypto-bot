package io.algopilot.portfolio.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.algopilot.fill.Fill;
import io.algopilot.fill.FillStore;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioAccountingServiceTest {
  private PositionStore positionStore;
  private FillStore fillStore;
  private OrderStore orderStore;
  private PortfolioAccountingService service;

  @BeforeEach
  void setUp() {
    positionStore = mock(PositionStore.class);
    fillStore = mock(FillStore.class);
    orderStore = mock(OrderStore.class);
    service = new PortfolioAccountingService(positionStore, fillStore, orderStore);
  }

  @Test
  void testAccounting_startingCapitalWithoutPositions() {
    when(positionStore.findAll()).thenReturn(List.of());
    when(fillStore.findAll()).thenReturn(List.of());
    when(orderStore.findAllOpenOrders()).thenReturn(List.of());

    PortfolioSummary summary = service.calculateSummary(new BigDecimal("100000.00"));

    assertThat(summary.startingCapital()).isEqualByComparingTo("100000.00");
    assertThat(summary.cash()).isEqualByComparingTo("100000.00");
    assertThat(summary.costBasisExposure()).isEqualByComparingTo("0.00");
    assertThat(summary.marketExposure()).isEqualByComparingTo("0.00");
    assertThat(summary.grossExposure()).isEqualByComparingTo("0.00");
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo("0.00");
    assertThat(summary.realizedPnl()).isEqualByComparingTo("0.00");
    assertThat(summary.cumulativeFees()).isEqualByComparingTo("0.00");
    assertThat(summary.totalNetPnl()).isEqualByComparingTo("0.00");
    assertThat(summary.portfolioEquity()).isEqualByComparingTo("100000.00");
  }

  @Test
  void testAccounting_openPositionMarkedToMarket() {
    // Starting Capital = $100,000
    // Open position: 100 shares @ $100 = $10,000 cost basis
    // Current market price = $110 -> Position market value = $11,000
    // Expected: cash = $90,000, market value = $11,000, equity = $101,000, unrealized P&L = $1,000, realized P&L = $0
    Position pos = new Position(UUID.randomUUID(), "bot-1", "AAPL", new BigDecimal("100"), new BigDecimal("100.00"), BigDecimal.ZERO, Instant.now());
    when(positionStore.findAll()).thenReturn(List.of(pos));
    when(fillStore.findAll()).thenReturn(List.of());
    when(orderStore.findAllOpenOrders()).thenReturn(List.of());

    service.updateMarketPrice("AAPL", new BigDecimal("110.00"));
    PortfolioSummary summary = service.calculateSummary(new BigDecimal("100000.00"));

    assertThat(summary.startingCapital()).isEqualByComparingTo("100000.00");
    assertThat(summary.cash()).isEqualByComparingTo("90000.00");
    assertThat(summary.costBasisExposure()).isEqualByComparingTo("10000.00");
    assertThat(summary.marketExposure()).isEqualByComparingTo("11000.00");
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo("1000.00");
    assertThat(summary.realizedPnl()).isEqualByComparingTo("0.00");
    assertThat(summary.cumulativeFees()).isEqualByComparingTo("0.00");
    assertThat(summary.totalNetPnl()).isEqualByComparingTo("1000.00");
    assertThat(summary.portfolioEquity()).isEqualByComparingTo("101000.00");
  }

  @Test
  void testAccounting_closedPositionWithRealizedPnlAndFees() {
    // Starting Capital = $100,000
    // Position was closed with $1,000 realized gain and $10 total fees
    // Quantity is now 0.
    // Expected: cash = $100,990, market value = $0, unrealized P&L = $0, realized P&L = $1,000, fees = $10, net P&L = $990, equity = $100,990
    Position closedPos = new Position(UUID.randomUUID(), "bot-1", "AAPL", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000.00"), Instant.now());
    Fill fill1 = new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-1", new BigDecimal("100"), new BigDecimal("100.00"), new BigDecimal("5.00"), Instant.now());
    Fill fill2 = new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-2", new BigDecimal("100"), new BigDecimal("110.00"), new BigDecimal("5.00"), Instant.now());

    when(positionStore.findAll()).thenReturn(List.of(closedPos));
    when(fillStore.findAll()).thenReturn(List.of(fill1, fill2));
    when(orderStore.findAllOpenOrders()).thenReturn(List.of());

    PortfolioSummary summary = service.calculateSummary(new BigDecimal("100000.00"));

    assertThat(summary.cash()).isEqualByComparingTo("100990.00");
    assertThat(summary.marketExposure()).isEqualByComparingTo("0.00");
    assertThat(summary.costBasisExposure()).isEqualByComparingTo("0.00");
    assertThat(summary.unrealizedPnl()).isEqualByComparingTo("0.00");
    assertThat(summary.realizedPnl()).isEqualByComparingTo("1000.00");
    assertThat(summary.cumulativeFees()).isEqualByComparingTo("10.00");
    assertThat(summary.netRealizedPnl()).isEqualByComparingTo("990.00");
    assertThat(summary.totalNetPnl()).isEqualByComparingTo("990.00");
    assertThat(summary.portfolioEquity()).isEqualByComparingTo("100990.00");
  }

  @Test
  void testAccounting_lossTradeWithFees() {
    // Starting Capital = $100,000
    // Position bought for $10,000 (100 @ $100), closed for $9,000 (100 @ $90) with $5 fee
    // Realized loss = -$1,000, fee = $5
    // Cash = $100,000 - $1,000 - $5 = $98,995
    Position closedLoss = new Position(UUID.randomUUID(), "bot-1", "NVDA", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1000.00"), Instant.now());
    Fill fill = new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-loss", new BigDecimal("100"), new BigDecimal("90.00"), new BigDecimal("5.00"), Instant.now());

    when(positionStore.findAll()).thenReturn(List.of(closedLoss));
    when(fillStore.findAll()).thenReturn(List.of(fill));
    when(orderStore.findAllOpenOrders()).thenReturn(List.of());

    PortfolioSummary summary = service.calculateSummary(new BigDecimal("100000.00"));

    assertThat(summary.cash()).isEqualByComparingTo("98995.00");
    assertThat(summary.realizedPnl()).isEqualByComparingTo("-1000.00");
    assertThat(summary.cumulativeFees()).isEqualByComparingTo("5.00");
    assertThat(summary.totalNetPnl()).isEqualByComparingTo("-1005.00");
    assertThat(summary.portfolioEquity()).isEqualByComparingTo("98995.00");
  }

  @Test
  void testAccounting_pendingOrderReservations() {
    // Open position: 50 TSLA @ $200 = $10,000
    Position pos = new Position(UUID.randomUUID(), "bot-1", "TSLA", new BigDecimal("50"), new BigDecimal("200.00"), BigDecimal.ZERO, Instant.now());
    // Pending order: 25 NVDA @ $400 = $10,000
    OrderRecord pendingOrder = new OrderRecord(
        UUID.randomUUID(), "client-res-1", "bot-1", "v1", "NVDA",
        RiskDecisionRequest.Side.BUY, new BigDecimal("25"), new BigDecimal("400.00"),
        OrderStatus.CREATED, Instant.now()
    );

    when(positionStore.findAll()).thenReturn(List.of(pos));
    when(fillStore.findAll()).thenReturn(List.of());
    when(orderStore.findAllOpenOrders()).thenReturn(List.of(pendingOrder));

    service.updateMarketPrice("TSLA", new BigDecimal("200.00"));
    PortfolioSummary summary = service.calculateSummary(new BigDecimal("100000.00"));

    assertThat(summary.cash()).isEqualByComparingTo("90000.00");
    assertThat(summary.marketExposure()).isEqualByComparingTo("10000.00");
    assertThat(summary.portfolioEquity()).isEqualByComparingTo("100000.00");
    assertThat(summary.pendingOrderNotional()).isEqualByComparingTo("10000.00");
    assertThat(summary.totalReservedExposure()).isEqualByComparingTo("20000.00");
    assertThat(summary.riskUtilizationPercent()).isEqualByComparingTo("20.0000");
  }
}
