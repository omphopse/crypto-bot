package io.algopilot.portfolio.accounting;

import io.algopilot.fill.Fill;
import io.algopilot.fill.FillStore;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAccountingService {
  public static final BigDecimal DEFAULT_STARTING_CAPITAL = new BigDecimal("100000.00");

  private final PositionStore positionStore;
  private final FillStore fillStore;
  private final OrderStore orderStore;
  private final Clock clock;
  private final Map<String, BigDecimal> verifiedMarketPrices = new ConcurrentHashMap<>();

  @org.springframework.beans.factory.annotation.Autowired
  public PortfolioAccountingService(
      PositionStore positionStore,
      FillStore fillStore,
      OrderStore orderStore,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this.positionStore = positionStore;
    this.fillStore = fillStore;
    this.orderStore = orderStore;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public PortfolioAccountingService(
      PositionStore positionStore,
      FillStore fillStore,
      OrderStore orderStore) {
    this(positionStore, fillStore, orderStore, Clock.systemUTC());
  }

  public void updateMarketPrice(String symbol, BigDecimal price) {
    if (symbol != null && price != null && price.compareTo(BigDecimal.ZERO) > 0) {
      verifiedMarketPrices.put(symbol.toUpperCase(), price);
    }
  }

  public BigDecimal getCurrentPrice(String symbol, BigDecimal fallbackPrice) {
    if (symbol == null) return fallbackPrice != null ? fallbackPrice : BigDecimal.ZERO;
    return verifiedMarketPrices.getOrDefault(symbol.toUpperCase(), fallbackPrice != null ? fallbackPrice : BigDecimal.ZERO);
  }

  public PortfolioSummary calculateSummary() {
    return calculateSummary(DEFAULT_STARTING_CAPITAL);
  }

  public PortfolioSummary calculateSummary(BigDecimal startingCapital) {
    BigDecimal capital = startingCapital != null ? startingCapital : DEFAULT_STARTING_CAPITAL;
    List<Position> positions = positionStore != null ? positionStore.findAll() : List.of();
    List<Fill> fills = fillStore != null ? fillStore.findAll() : List.of();
    List<OrderRecord> openOrders = orderStore != null ? orderStore.findAllOpenOrders() : List.of();

    BigDecimal cumulativeFees = fills.stream()
        .map(Fill::fee)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal realizedPnl = positions.stream()
        .map(Position::realizedPnl)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal costBasisExposure = BigDecimal.ZERO;
    BigDecimal longCostBasis = BigDecimal.ZERO;
    BigDecimal shortCostBasis = BigDecimal.ZERO;
    BigDecimal marketExposure = BigDecimal.ZERO;
    BigDecimal grossExposure = BigDecimal.ZERO;
    BigDecimal unrealizedPnl = BigDecimal.ZERO;

    for (Position p : positions) {
      if (p.quantity().signum() == 0) continue;
      BigDecimal price = getCurrentPrice(p.symbol(), p.averageEntryPrice());
      BigDecimal cost = p.quantity().multiply(p.averageEntryPrice());
      BigDecimal absCost = p.quantity().abs().multiply(p.averageEntryPrice());
      BigDecimal mktVal = p.quantity().multiply(price);
      BigDecimal absMktVal = p.quantity().abs().multiply(price);
      BigDecimal uPnl = p.quantity().multiply(price.subtract(p.averageEntryPrice()));

      costBasisExposure = costBasisExposure.add(absCost);
      if (p.quantity().signum() > 0) {
        longCostBasis = longCostBasis.add(cost);
      } else {
        shortCostBasis = shortCostBasis.add(cost.abs());
      }
      marketExposure = marketExposure.add(mktVal);
      grossExposure = grossExposure.add(absMktVal);
      unrealizedPnl = unrealizedPnl.add(uPnl);
    }

    BigDecimal netRealizedPnl = realizedPnl.subtract(cumulativeFees);
    BigDecimal totalNetPnl = realizedPnl.add(unrealizedPnl).subtract(cumulativeFees);
    BigDecimal cash = capital.subtract(longCostBasis).add(shortCostBasis).add(realizedPnl).subtract(cumulativeFees);
    BigDecimal portfolioEquity = cash.add(marketExposure);

    BigDecimal pendingOrderNotional = openOrders.stream()
        .map(o -> o.quantity().multiply(o.referencePrice()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalReservedExposure = grossExposure.add(pendingOrderNotional);
    BigDecimal riskUtilizationPercent = portfolioEquity.compareTo(BigDecimal.ZERO) > 0
        ? totalReservedExposure.multiply(new BigDecimal("100")).divide(portfolioEquity, 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    return new PortfolioSummary(
        capital.setScale(2, RoundingMode.HALF_UP),
        cash.setScale(2, RoundingMode.HALF_UP),
        costBasisExposure.setScale(2, RoundingMode.HALF_UP),
        marketExposure.setScale(2, RoundingMode.HALF_UP),
        grossExposure.setScale(2, RoundingMode.HALF_UP),
        unrealizedPnl.setScale(2, RoundingMode.HALF_UP),
        realizedPnl.setScale(2, RoundingMode.HALF_UP),
        cumulativeFees.setScale(2, RoundingMode.HALF_UP),
        netRealizedPnl.setScale(2, RoundingMode.HALF_UP),
        totalNetPnl.setScale(2, RoundingMode.HALF_UP),
        portfolioEquity.setScale(2, RoundingMode.HALF_UP),
        pendingOrderNotional.setScale(2, RoundingMode.HALF_UP),
        totalReservedExposure.setScale(2, RoundingMode.HALF_UP),
        riskUtilizationPercent.setScale(2, RoundingMode.HALF_UP),
        clock.instant()
    );
  }

  public List<PositionMark> getMarkedPositions() {
    List<Position> positions = positionStore != null ? positionStore.findAll() : List.of();
    Instant now = clock.instant();

    return positions.stream().map(p -> {
      BigDecimal price = getCurrentPrice(p.symbol(), p.averageEntryPrice());
      BigDecimal costBasis = p.quantity().multiply(p.averageEntryPrice());
      BigDecimal marketValue = p.quantity().multiply(price);
      BigDecimal unrealizedPnl = p.quantity().multiply(price.subtract(p.averageEntryPrice()));

      return new PositionMark(
          p.id(),
          p.botId(),
          p.symbol(),
          p.quantity(),
          p.averageEntryPrice().setScale(2, RoundingMode.HALF_UP),
          price.setScale(2, RoundingMode.HALF_UP),
          costBasis.setScale(2, RoundingMode.HALF_UP),
          marketValue.setScale(2, RoundingMode.HALF_UP),
          unrealizedPnl.setScale(2, RoundingMode.HALF_UP),
          p.realizedPnl().setScale(2, RoundingMode.HALF_UP),
          now
      );
    }).toList();
  }
}
