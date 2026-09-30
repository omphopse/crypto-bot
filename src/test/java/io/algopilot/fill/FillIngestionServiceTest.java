package io.algopilot.fill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.order.OrderLifecycleService;
import io.algopilot.order.OrderRecord;
import io.algopilot.order.OrderStatus;
import io.algopilot.order.OrderStore;
import io.algopilot.portfolio.PositionAccountingService;
import io.algopilot.risk.RiskDecisionRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FillIngestionServiceTest {
  private FillStore fillStore;
  private OrderStore orderStore;
  private OrderLifecycleService lifecycleService;
  private PositionAccountingService positionAccounting;
  private AuditEventWriter auditEventWriter;
  private FillIngestionService service;

  private final Instant fixedNow = Instant.parse("2026-09-12T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    fillStore = mock(FillStore.class);
    orderStore = mock(OrderStore.class);
    lifecycleService = mock(OrderLifecycleService.class);
    positionAccounting = mock(PositionAccountingService.class);
    auditEventWriter = mock(AuditEventWriter.class);

    service = new FillIngestionService(
        fillStore, orderStore, lifecycleService, positionAccounting, auditEventWriter, fixedClock
    );
  }

  @Test
  void testInvariantG_idempotentFillReplay() {
    // Invariant G: Replaying a duplicate fill report MUST return the existing fill
    // and MUST NOT apply duplicate position changes or order lifecycle transitions.
    UUID orderId = UUID.randomUUID();
    UUID fillId = UUID.randomUUID();
    Fill existingFill = new Fill(
        fillId, orderId, "alpaca-fill-12345",
        new BigDecimal("0.000132"), new BigDecimal("77000.00"),
        BigDecimal.ZERO, fixedNow
    );

    when(fillStore.findByExchangeFillId("alpaca-fill-12345")).thenReturn(Optional.of(existingFill));

    FillReport duplicateReport = new FillReport(
        orderId, "alpaca-fill-12345",
        new BigDecimal("0.000132"), new BigDecimal("77000.00"),
        BigDecimal.ZERO
    );

    Fill result = service.ingest(duplicateReport);

    assertThat(result).isSameAs(existingFill);
    // Crucial: verify no duplicate operations were executed
    verify(fillStore, never()).save(any());
    verify(positionAccounting, never()).apply(any(), any(), any(), any());
    verify(lifecycleService, never()).transition(any(), any());
    verify(auditEventWriter, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void testIngest_firstTimeFill_appliesPositionAndTransitionsOrder() {
    UUID orderId = UUID.randomUUID();
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-1", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("0.000132"), new BigDecimal("77000.00"),
        OrderStatus.SUBMITTED, fixedNow
    );

    when(fillStore.findByExchangeFillId("alpaca-fill-99999")).thenReturn(Optional.empty());
    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(fillStore.totalQuantityForOrder(orderId)).thenReturn(BigDecimal.ZERO);
    when(fillStore.save(any(Fill.class))).thenAnswer(inv -> inv.getArgument(0));

    FillReport report = new FillReport(
        orderId, "alpaca-fill-99999",
        new BigDecimal("0.000132"), new BigDecimal("77000.00"),
        new BigDecimal("0.01")
    );

    Fill result = service.ingest(report);

    assertThat(result).isNotNull();
    assertThat(result.orderId()).isEqualTo(orderId);
    assertThat(result.exchangeFillId()).isEqualTo("alpaca-fill-99999");
    assertThat(result.quantity()).isEqualByComparingTo("0.000132");

    verify(fillStore).save(any(Fill.class));
    verify(positionAccounting).apply(eq(order), eq(new BigDecimal("0.000132")), eq(new BigDecimal("77000.00")), eq(new BigDecimal("0.01")));
    verify(lifecycleService).transition(eq(orderId), argThat(req -> req.targetStatus() == OrderStatus.FILLED));
    verify(auditEventWriter).record(eq("EXECUTION"), eq("bot-1"), eq("FILL_RECORDED"), eq("ORDER"), eq(orderId.toString()), any());
  }

  @Test
  void testIngest_orderNotFound_throwsException() {
    UUID orderId = UUID.randomUUID();
    when(fillStore.findByExchangeFillId("alpaca-fill-unknown")).thenReturn(Optional.empty());
    when(orderStore.findById(orderId)).thenReturn(Optional.empty());

    FillReport report = new FillReport(
        orderId, "alpaca-fill-unknown",
        new BigDecimal("1"), new BigDecimal("100"),
        BigDecimal.ZERO
    );

    assertThatThrownBy(() -> service.ingest(report))
        .isInstanceOf(FillRejectedException.class)
        .hasMessage("ORDER_NOT_FOUND");
  }

  @Test
  void testIngest_orderNotFillable_throwsException() {
    UUID orderId = UUID.randomUUID();
    OrderRecord cancelledOrder = new OrderRecord(
        orderId, "client-ord-2", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("100"),
        OrderStatus.CANCELLED, fixedNow
    );

    when(fillStore.findByExchangeFillId("alpaca-fill-cancelled")).thenReturn(Optional.empty());
    when(orderStore.findById(orderId)).thenReturn(Optional.of(cancelledOrder));

    FillReport report = new FillReport(
        orderId, "alpaca-fill-cancelled",
        new BigDecimal("1"), new BigDecimal("100"),
        BigDecimal.ZERO
    );

    assertThatThrownBy(() -> service.ingest(report))
        .isInstanceOf(FillRejectedException.class)
        .hasMessage("ORDER_NOT_FILLABLE");
  }

  @Test
  void testIngest_fillExceedsOrderQuantity_throwsException() {
    UUID orderId = UUID.randomUUID();
    OrderRecord order = new OrderRecord(
        orderId, "client-ord-3", "bot-1", "v1", "BTC/USD",
        RiskDecisionRequest.Side.BUY, new BigDecimal("1"), new BigDecimal("100"),
        OrderStatus.SUBMITTED, fixedNow
    );

    when(fillStore.findByExchangeFillId("alpaca-fill-excess")).thenReturn(Optional.empty());
    when(orderStore.findById(orderId)).thenReturn(Optional.of(order));
    when(fillStore.totalQuantityForOrder(orderId)).thenReturn(new BigDecimal("0.8"));

    FillReport report = new FillReport(
        orderId, "alpaca-fill-excess",
        new BigDecimal("0.5"), new BigDecimal("100"),
        BigDecimal.ZERO
    );

    assertThatThrownBy(() -> service.ingest(report))
        .isInstanceOf(FillRejectedException.class)
        .hasMessage("FILL_EXCEEDS_ORDER_QUANTITY");
  }
}
