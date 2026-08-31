package io.algopilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;

import io.algopilot.fill.Fill;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.portfolio.Position;
import io.algopilot.reconciliation.broker.BrokerAccountBalance;
import io.algopilot.reconciliation.broker.BrokerFill;
import io.algopilot.reconciliation.broker.BrokerOrder;
import io.algopilot.reconciliation.broker.BrokerPosition;
import io.algopilot.reconciliation.broker.BrokerStateSnapshot;
import io.algopilot.reconciliation.engine.LocalStateSnapshot;
import io.algopilot.reconciliation.engine.ReconciliationEngine;
import io.algopilot.reconciliation.model.MismatchCategory;
import io.algopilot.reconciliation.model.MismatchSeverity;
import io.algopilot.reconciliation.model.MismatchType;
import io.algopilot.reconciliation.model.ReconciliationMismatch;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReconciliationEngineTest {
  private ReconciliationEngine engine;
  private UUID runId;
  private String botId;
  private Instant now;

  @BeforeEach
  void setUp() {
    engine = new ReconciliationEngine();
    runId = UUID.randomUUID();
    botId = UUID.randomUUID().toString();
    now = Instant.now();
  }

  @Test
  void testMatchingState_returnsZeroMismatches() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId,
        new BigDecimal("10000.00"),
        new BigDecimal("20000.00"),
        new BigDecimal("15000.00"),
        List.of(new OrderRecord(UUID.randomUUID(), "order-1", botId, "strat-1", "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.5"), new BigDecimal("60000.00"), OrderStatus.SUBMITTED, now)),
        List.of(new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-1", new BigDecimal("1.0"), new BigDecimal("59950.00"), new BigDecimal("2.50"), now)),
        List.of(new Position(UUID.randomUUID(), botId, "BTC/USD", new BigDecimal("1.0"), new BigDecimal("59950.00"), BigDecimal.ZERO, now)),
        now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER,
        io.algopilot.bot.ExecutionMode.PAPER,
        botId,
        new BrokerAccountBalance("USD", new BigDecimal("10000.00"), new BigDecimal("20000.00"), new BigDecimal("15000.00"), now),
        List.of(new BrokerOrder("brk-1", "order-1", botId, "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.5"), BigDecimal.ZERO, new BigDecimal("60000.00"), OrderStatus.SUBMITTED, now, now)),
        List.of(new BrokerFill("fill-1", "brk-1", "order-1", botId, "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("59950.00"), new BigDecimal("2.50"), now)),
        List.of(new BrokerPosition(botId, "BTC/USD", new BigDecimal("1.0"), new BigDecimal("59950.00"), BigDecimal.ZERO, new BigDecimal("59950.00"), now)),
        now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertTrue(mismatches.isEmpty(), "Expected clean match with zero mismatches");
  }

  @Test
  void testBalanceMismatch_detectedCorrectly() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId,
        new BigDecimal("10000.00"),
        new BigDecimal("20000.00"),
        new BigDecimal("15000.00"),
        List.of(), List.of(), List.of(), now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER,
        io.algopilot.bot.ExecutionMode.PAPER,
        botId,
        new BrokerAccountBalance("USD", new BigDecimal("9500.00"), new BigDecimal("20000.00"), new BigDecimal("14500.00"), now),
        List.of(), List.of(), List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(2, mismatches.size());
    assertTrue(mismatches.stream().allMatch(m -> m.category() == MismatchCategory.BALANCE_MISMATCH));
    assertTrue(mismatches.stream().allMatch(m -> m.severity() == MismatchSeverity.CRITICAL));
  }

  @Test
  void testOrderMismatch_localMissingOnBroker() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null,
        List.of(new OrderRecord(UUID.randomUUID(), "order-missing", botId, "strat-1", "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"), OrderStatus.SUBMITTED, now)),
        List.of(), List.of(), now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(), List.of(), List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(1, mismatches.size());
    assertEquals(MismatchType.LOCAL_ORDER_MISSING_BROKER_ORDER, mismatches.getFirst().mismatchType());
    assertEquals(MismatchSeverity.CRITICAL, mismatches.getFirst().severity());
  }

  @Test
  void testOrderMismatch_brokerMissingLocally() {
    LocalStateSnapshot local = new LocalStateSnapshot(botId, null, null, null, List.of(), List.of(), List.of(), now);

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null,
        List.of(new BrokerOrder("brk-phantom", "order-phantom", botId, "BTC/USD", RiskDecisionRequest.Side.SELL, new BigDecimal("2.0"), BigDecimal.ZERO, new BigDecimal("61000.00"), OrderStatus.SUBMITTED, now, now)),
        List.of(), List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(1, mismatches.size());
    assertEquals(MismatchType.BROKER_ORDER_MISSING_LOCAL_ORDER, mismatches.getFirst().mismatchType());
    assertEquals(MismatchSeverity.CRITICAL, mismatches.getFirst().severity());
  }

  @Test
  void testOrderMismatch_quantityAndStatusMismatch() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null,
        List.of(new OrderRecord(UUID.randomUUID(), "order-1", botId, "strat-1", "ETH/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("10.0"), new BigDecimal("3000.00"), OrderStatus.SUBMITTED, now)),
        List.of(), List.of(), now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null,
        List.of(new BrokerOrder("brk-1", "order-1", botId, "ETH/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("5.0"), BigDecimal.ZERO, new BigDecimal("3000.00"), OrderStatus.PARTIALLY_FILLED, now, now)),
        List.of(), List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(2, mismatches.size());
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.ORDER_QUANTITY_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.ORDER_STATUS_MISMATCH));
  }

  @Test
  void testFillMismatch_missingLocallyAndMissingOnBroker() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null, List.of(),
        List.of(new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-local-only", new BigDecimal("1.0"), new BigDecimal("100.0"), BigDecimal.ZERO, now)),
        List.of(), now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(),
        List.of(new BrokerFill("fill-broker-only", "brk-1", "order-1", botId, "AAPL", RiskDecisionRequest.Side.BUY, new BigDecimal("2.0"), new BigDecimal("150.0"), BigDecimal.ZERO, now)),
        List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(2, mismatches.size());
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.FILL_MISSING_BROKER));
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.FILL_MISSING_LOCALLY));
  }

  @Test
  void testFillMismatch_quantityAndPriceDiscrepancy() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null, List.of(),
        List.of(new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-1", new BigDecimal("5.0"), new BigDecimal("100.00"), new BigDecimal("1.00"), now)),
        List.of(), now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(),
        List.of(new BrokerFill("fill-1", "brk-1", "order-1", botId, "NVDA", RiskDecisionRequest.Side.BUY, new BigDecimal("4.0"), new BigDecimal("102.00"), new BigDecimal("1.50"), now)),
        List.of(), now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(3, mismatches.size());
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.FILL_QUANTITY_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.FILL_PRICE_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.mismatchType() == MismatchType.FILL_FEE_MISMATCH));
  }

  @Test
  void testPositionMismatch_quantityAndSideMismatch() {
    // Local: LONG 5.0, Broker: SHORT -5.0
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null, List.of(), List.of(),
        List.of(new Position(UUID.randomUUID(), botId, "BTC/USD", new BigDecimal("5.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, now)),
        now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(), List.of(),
        List.of(new BrokerPosition(botId, "BTC/USD", new BigDecimal("-5.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, new BigDecimal("-300000.00"), now)),
        now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(1, mismatches.size());
    assertEquals(MismatchType.POSITION_SIDE_MISMATCH, mismatches.getFirst().mismatchType());
    assertEquals(MismatchSeverity.CRITICAL, mismatches.getFirst().severity());
  }

  @Test
  void testPositionMismatch_quantityOnly() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null, List.of(), List.of(),
        List.of(new Position(UUID.randomUUID(), botId, "BTC/USD", new BigDecimal("10.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, now)),
        now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(), List.of(),
        List.of(new BrokerPosition(botId, "BTC/USD", new BigDecimal("8.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, new BigDecimal("480000.00"), now)),
        now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(1, mismatches.size());
    assertEquals(MismatchType.POSITION_QUANTITY_MISMATCH, mismatches.getFirst().mismatchType());
    assertEquals(MismatchSeverity.CRITICAL, mismatches.getFirst().severity());
  }

  @Test
  void testPositionMismatch_priceOnly() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId, null, null, null, List.of(), List.of(),
        List.of(new Position(UUID.randomUUID(), botId, "BTC/USD", new BigDecimal("10.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, now)),
        now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        null, List.of(), List.of(),
        List.of(new BrokerPosition(botId, "BTC/USD", new BigDecimal("10.0"), new BigDecimal("59000.00"), BigDecimal.ZERO, new BigDecimal("590000.00"), now)),
        now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(1, mismatches.size());
    assertEquals(MismatchType.POSITION_PRICE_MISMATCH, mismatches.getFirst().mismatchType());
    assertEquals(MismatchSeverity.WARNING, mismatches.getFirst().severity());
  }

  @Test
  void testMultipleSimultaneousMismatches_allDetected() {
    LocalStateSnapshot local = new LocalStateSnapshot(
        botId,
        new BigDecimal("10000.00"), null, null,
        List.of(new OrderRecord(UUID.randomUUID(), "order-1", botId, "strat-1", "BTC/USD", RiskDecisionRequest.Side.BUY, new BigDecimal("1.0"), new BigDecimal("60000.00"), OrderStatus.SUBMITTED, now)),
        List.of(new Fill(UUID.randomUUID(), UUID.randomUUID(), "fill-1", new BigDecimal("1.0"), new BigDecimal("60000.00"), BigDecimal.ZERO, now)),
        List.of(new Position(UUID.randomUUID(), botId, "ETH/USD", new BigDecimal("5.0"), new BigDecimal("3000.00"), BigDecimal.ZERO, now)),
        now
    );

    BrokerStateSnapshot broker = new BrokerStateSnapshot(
        io.algopilot.bot.Broker.ALPACA_PAPER, io.algopilot.bot.ExecutionMode.PAPER, botId,
        new BrokerAccountBalance("USD", new BigDecimal("9000.00"), BigDecimal.ZERO, BigDecimal.ZERO, now), // balance mismatch
        List.of(), // order missing on broker
        List.of(), // fill missing on broker
        List.of(new BrokerPosition(botId, "ETH/USD", new BigDecimal("2.0"), new BigDecimal("3000.00"), BigDecimal.ZERO, BigDecimal.ZERO, now)), // position mismatch
        now
    );

    List<ReconciliationMismatch> mismatches = engine.reconcile(runId, botId, local, broker);
    assertEquals(4, mismatches.size());
    assertTrue(mismatches.stream().anyMatch(m -> m.category() == MismatchCategory.BALANCE_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.category() == MismatchCategory.ORDER_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.category() == MismatchCategory.FILL_MISMATCH));
    assertTrue(mismatches.stream().anyMatch(m -> m.category() == MismatchCategory.POSITION_MISMATCH));
  }
}
