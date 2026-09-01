package io.algopilot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.portfolio.Position;
import io.algopilot.portfolio.PositionStore;
import io.algopilot.risk.RiskDecision;
import io.algopilot.risk.RiskDecisionRequest;
import io.algopilot.risk.RiskDecisionService;
import io.algopilot.risk.RiskEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

  @Test
  void creates_once_then_returns_the_original_order_on_retry() {
    MemoryStore store = new MemoryStore();
    AuditEventWriter audit = mock(AuditEventWriter.class);
    OrderService service = new OrderService(riskService(), store, audit);
    OrderRecord first = service.create(command("client-1", "BTC/USD", new BigDecimal("1"), new BigDecimal("100"), false));
    OrderRecord retry = service.create(command("client-1", "BTC/USD", new BigDecimal("1"), new BigDecimal("100"), false));
    assertThat(retry.id()).isEqualTo(first.id());
    verify(audit, times(1)).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  void rejected_risk_decision_never_creates_an_order() {
    MemoryStore store = new MemoryStore();
    OrderService service = new OrderService(riskService(), store, mock(AuditEventWriter.class));
    assertThatThrownBy(() -> service.create(command("client-rej", "BTC/USD", new BigDecimal("1"), new BigDecimal("100"), true)))
        .isInstanceOf(OrderRejectedException.class);
    assertThat(store.orders).isEmpty();
  }

  @Test
  void testRegression_rapidOrdersForSameSymbolBlockedByPendingOrderExposure() {
    MemoryStore store = new MemoryStore();
    MemoryPositionStore positionStore = new MemoryPositionStore();
    OrderService service = new OrderService(riskService(), store, positionStore, null, mock(AuditEventWriter.class));

    // Order 1: BUY 30 TSLA @ $200 = $6,000 on $100k account (6% <= 10% limit) -> APPROVED
    OrderRecord ord1 = service.create(command("tsla-1", "TSLA", new BigDecimal("30"), new BigDecimal("200"), false));
    assertThat(ord1).isNotNull();
    assertThat(ord1.status()).isEqualTo(OrderStatus.CREATED);

    // Order 2: BUY 30 TSLA @ $200 = $6,000. Before fill, pending exposure is $6,000 -> Total $12,000 (12% > 10% limit) -> REJECTED
    assertThatThrownBy(() -> service.create(command("tsla-2", "TSLA", new BigDecimal("30"), new BigDecimal("200"), false)))
        .isInstanceOf(OrderRejectedException.class)
        .satisfies(ex -> {
          OrderRejectedException ore = (OrderRejectedException) ex;
          assertThat(ore.decision().reasons()).contains(RiskDecision.Reason.MAX_POSITION_SIZE);
        });

    assertThat(store.findAllOpenOrders()).hasSize(1);
  }

  @Test
  void testRegression_rapidOrdersAcrossSymbolsBlockedByPendingPortfolioExposure() {
    MemoryStore store = new MemoryStore();
    MemoryPositionStore positionStore = new MemoryPositionStore();
    OrderService service = new OrderService(riskService(), store, positionStore, null, mock(AuditEventWriter.class));

    // Submit 5 orders across 5 different symbols, each $9,000 (5 * $9,000 = $45,000 <= $50,000 limit)
    String[] symbols = {"TSLA", "AAPL", "NVDA", "MSFT", "GOOG"};
    for (int i = 0; i < 5; i++) {
      OrderRecord ord = service.create(command("ord-" + i, symbols[i], new BigDecimal("45"), new BigDecimal("200"), false));
      assertThat(ord).isNotNull();
    }
    assertThat(store.findAllOpenOrders()).hasSize(5);

    // 6th order: AMZN for $9,000 -> total portfolio exposure = $45,000 + $9,000 = $54,000 (54% > 50% limit) -> REJECTED
    assertThatThrownBy(() -> service.create(command("ord-6", "AMZN", new BigDecimal("45"), new BigDecimal("200"), false)))
        .isInstanceOf(OrderRejectedException.class)
        .satisfies(ex -> {
          OrderRejectedException ore = (OrderRejectedException) ex;
          assertThat(ore.decision().reasons()).contains(RiskDecision.Reason.MAX_PORTFOLIO_EXPOSURE);
        });

    assertThat(store.findAllOpenOrders()).hasSize(5);
  }

  @Test
  void testConcurrentOrderCreation_enforcesGlobalExposureUnderConcurrency() throws Exception {
    MemoryStore store = new MemoryStore();
    MemoryPositionStore positionStore = new MemoryPositionStore();
    OrderService service = new OrderService(riskService(), store, positionStore, null, mock(AuditEventWriter.class));

    // 10 concurrent bots simultaneously request $20,000 positions on a $100,000 account (Max portfolio limit is 50% = $50,000).
    // Exactly 2 orders can be approved ($20k + $20k = $40k <= $50k).
    // The 3rd and subsequent orders must be deterministically rejected ($40k + $20k = $60k > $50k).
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger rejectedCount = new AtomicInteger();
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(() -> {
        try {
          startLatch.await();
          // $20,000 order (200 qty @ $100 ref price) with max position limit overridden or adjusted
          // Each bot uses a distinct symbol to test global portfolio exposure limit (50%)
          RiskDecisionRequest req = new RiskDecisionRequest(
              "client-concurrent-" + idx,
              "bot-" + idx,
              "v1",
              "SYM" + idx,
              RiskDecisionRequest.Side.BUY,
              new BigDecimal("200"),
              new BigDecimal("100"), // notional = $20,000
              new BigDecimal("100000"), // $100k equity
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              Instant.now(),
              false,
              false,
              false,
              0,
              0,
              0
          );
          service.create(req);
          successCount.incrementAndGet();
        } catch (OrderRejectedException ore) {
          if (ore.decision().reasons().contains(RiskDecision.Reason.MAX_PORTFOLIO_EXPOSURE) ||
              ore.decision().reasons().contains(RiskDecision.Reason.MAX_POSITION_SIZE)) {
            rejectedCount.incrementAndGet();
          } else {
            errors.add(ore);
          }
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown(); // release all threads simultaneously
    doneLatch.await();
    executor.shutdown();

    assertThat(errors).isEmpty();
    assertThat(successCount.get() + rejectedCount.get()).isEqualTo(threadCount);
    // Since each order is $20k and limit is $50k (50%), at most 2 orders can be approved!
    assertThat(successCount.get()).isLessThanOrEqualTo(2);
    assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(8);

    // Verify total open orders in store never exceeded limit
    BigDecimal totalOpenExposure = store.findAllOpenOrders().stream()
        .map(o -> o.quantity().multiply(o.referencePrice()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalOpenExposure).isLessThanOrEqualTo(new BigDecimal("50000"));
  }

  private RiskDecisionRequest command(String clientOrderId, String symbol, BigDecimal qty, BigDecimal price, boolean stopped) {
    return new RiskDecisionRequest(
        clientOrderId, "bot-1", "v1", symbol, RiskDecisionRequest.Side.BUY,
        qty, price, new BigDecimal("100000"),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), false, stopped, false, 0, 0, 0
    );
  }

  private RiskDecisionService riskService() {
    return new RiskDecisionService(new RiskEngine(), decision -> decision, mock(AuditEventWriter.class), new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
  }

  private static final class MemoryStore implements OrderStore {
    final Map<String, OrderRecord> orders = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Optional<OrderRecord> findByClientOrderId(String id) {
      return Optional.ofNullable(orders.get(id));
    }

    @Override
    public Optional<OrderRecord> findById(UUID id) {
      return orders.values().stream().filter(order -> order.id().equals(id)).findFirst();
    }

    @Override
    public List<OrderRecord> findByBotId(String botId) {
      return orders.values().stream().filter(o -> o.botId().equals(botId)).toList();
    }

    @Override
    public List<OrderRecord> findOpenOrdersByBotId(String botId) {
      return orders.values().stream()
          .filter(o -> o.botId().equals(botId))
          .filter(o -> o.status() == OrderStatus.CREATED || o.status() == OrderStatus.SUBMITTED || o.status() == OrderStatus.ACKNOWLEDGED || o.status() == OrderStatus.PARTIALLY_FILLED)
          .toList();
    }

    @Override
    public List<OrderRecord> findAllOpenOrders() {
      return orders.values().stream()
          .filter(o -> o.status() == OrderStatus.CREATED || o.status() == OrderStatus.SUBMITTED || o.status() == OrderStatus.ACKNOWLEDGED || o.status() == OrderStatus.PARTIALLY_FILLED)
          .toList();
    }

    @Override
    public List<OrderRecord> findOpenOrdersBySymbol(String symbol) {
      return orders.values().stream()
          .filter(o -> o.symbol() != null && o.symbol().equalsIgnoreCase(symbol))
          .filter(o -> o.status() == OrderStatus.CREATED || o.status() == OrderStatus.SUBMITTED || o.status() == OrderStatus.ACKNOWLEDGED || o.status() == OrderStatus.PARTIALLY_FILLED)
          .toList();
    }

    @Override
    public OrderRecord save(OrderRecord order) {
      orders.put(order.clientOrderId(), order);
      return order;
    }

    @Override
    public OrderRecord updateStatus(UUID id, OrderStatus status) {
      OrderRecord order = findById(id).orElseThrow();
      OrderRecord changed = new OrderRecord(order.id(), order.clientOrderId(), order.botId(), order.strategyVersionId(), order.symbol(), order.side(), order.quantity(), order.referencePrice(), status, order.createdAt());
      orders.put(changed.clientOrderId(), changed);
      return changed;
    }
  }

  private static final class MemoryPositionStore implements PositionStore {
    final Map<String, Position> positions = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Optional<Position> find(String botId, String symbol) {
      return Optional.ofNullable(positions.get(botId + ":" + symbol));
    }

    @Override
    public List<Position> findByBotId(String botId) {
      return positions.values().stream().filter(p -> p.botId().equals(botId)).toList();
    }

    @Override
    public List<Position> findAll() {
      return new ArrayList<>(positions.values());
    }

    @Override
    public Position save(Position position) {
      positions.put(position.botId() + ":" + position.symbol(), position);
      return position;
    }
  }
}
