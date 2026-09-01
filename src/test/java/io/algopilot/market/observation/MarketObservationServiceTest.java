package io.algopilot.market.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.algopilot.audit.AuditEventWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketObservationServiceTest {
  private MemoryMarketDataStore store;
  private AuditEventWriter audit;
  private Clock clock;
  private MarketObservationService service;

  @BeforeEach
  void setUp() {
    store = new MemoryMarketDataStore();
    audit = mock(AuditEventWriter.class);
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    service = new MarketObservationService(store, audit, clock, 60_000L);
  }

  @Test
  void testValidObservation_savedAndEmitsAudit() {
    Instant now = clock.instant();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("60000.00"), new BigDecimal("59995.00"), new BigDecimal("60005.00"),
        new BigDecimal("12.5"), new BigDecimal("59900.00"), new BigDecimal("60100.00"),
        new BigDecimal("59850.00"), new BigDecimal("60000.00"), "1m", now.minusSeconds(5), now, 60_000L
    );

    MarketObservation recorded = service.recordObservation(obs);

    assertThat(recorded.validationStatus()).isEqualTo("VALID");
    assertThat(recorded.isStale()).isFalse();
    assertThat(recorded.spread()).isEqualByComparingTo("10.00");
    assertThat(store.findLatest("BTC/USD")).isPresent();
    verify(audit, times(1)).record(eq("MARKET"), eq("BTC/USD"), eq("MARKET_OBSERVATION_RECEIVED"), eq("OBSERVATION"), eq(obs.id().toString()), anyMap());
  }

  @Test
  void testInvalidPrice_rejectedAndAudited() {
    Instant now = clock.instant();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "BTC/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("-10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "1m", now, now, 60_000L
    );

    MarketObservation recorded = service.recordObservation(obs);

    assertThat(recorded.validationStatus()).startsWith("INVALID:INVALID_PRICE");
    assertThat(store.findLatest("BTC/USD")).isEmpty();
    verify(audit, times(1)).record(eq("MARKET"), eq("BTC/USD"), eq("MARKET_OBSERVATION_REJECTED"), eq("OBSERVATION"), eq(obs.id().toString()), anyMap());
  }

  @Test
  void testCrossedMarket_bidGreaterThanAsk_rejected() {
    Instant now = clock.instant();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "ETH/USD", "BYBIT_DEMO", "DEMO",
        new BigDecimal("3000.00"), new BigDecimal("3010.00"), new BigDecimal("2990.00"), // bid > ask
        BigDecimal.TEN, new BigDecimal("3000.00"), new BigDecimal("3010.00"),
        new BigDecimal("2990.00"), new BigDecimal("3000.00"), "1m", now, now, 60_000L
    );

    MarketObservation recorded = service.recordObservation(obs);
    assertThat(recorded.validationStatus()).contains("CROSSED_MARKET");
    assertThat(store.findLatest("ETH/USD")).isEmpty();
  }

  @Test
  void testCorruptOhlc_highLessThanOpen_rejected() {
    Instant now = clock.instant();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "SOL/USD", "ALPACA_PAPER", "PAPER",
        new BigDecimal("150.00"), new BigDecimal("149.00"), new BigDecimal("151.00"),
        BigDecimal.TEN, new BigDecimal("155.00"), new BigDecimal("140.00"), // high (140) < open (155)
        new BigDecimal("130.00"), new BigDecimal("150.00"), "1m", now, now, 60_000L
    );

    MarketObservation recorded = service.recordObservation(obs);
    assertThat(recorded.validationStatus()).contains("INVALID_HIGH");
    assertThat(store.findLatest("SOL/USD")).isEmpty();
  }

  @Test
  void testStaleObservation_markedStaleAndAudited() {
    Instant now = clock.instant();
    Instant oldTimestamp = now.minusSeconds(120); // 2 minutes old > 1 minute threshold
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(), "AAPL", "ALPACA_PAPER", "PAPER",
        new BigDecimal("220.00"), new BigDecimal("219.90"), new BigDecimal("220.10"),
        new BigDecimal("500"), new BigDecimal("219.00"), new BigDecimal("221.00"),
        new BigDecimal("218.00"), new BigDecimal("220.00"), "1m", oldTimestamp, now, 60_000L
    );

    MarketObservation recorded = service.recordObservation(obs);
    assertThat(recorded.isStale()).isTrue();
    assertThat(recorded.validationStatus()).isEqualTo("STALE");
    verify(audit, times(1)).record(eq("MARKET"), eq("AAPL"), eq("MARKET_DATA_STALE"), eq("OBSERVATION"), eq(obs.id().toString()), anyMap());
  }

  private static final class MemoryMarketDataStore implements MarketDataStore {
    private final Map<String, List<MarketObservation>> map = Collections.synchronizedMap(new HashMap<>());

    @Override
    public MarketObservation save(MarketObservation obs) {
      map.computeIfAbsent(obs.symbol().toUpperCase(), k -> new ArrayList<>()).add(0, obs);
      return obs;
    }

    @Override
    public Optional<MarketObservation> findLatest(String symbol) {
      List<MarketObservation> list = map.get(symbol.toUpperCase());
      return (list != null && !list.isEmpty()) ? Optional.of(list.get(0)) : Optional.empty();
    }

    @Override
    public List<MarketObservation> findRecent(String symbol, int limit) {
      List<MarketObservation> list = map.getOrDefault(symbol.toUpperCase(), List.of());
      return list.subList(0, Math.min(list.size(), limit));
    }

    @Override
    public List<MarketObservation> findAllLatest() {
      List<MarketObservation> res = new ArrayList<>();
      map.values().forEach(list -> { if (!list.isEmpty()) res.add(list.get(0)); });
      return res;
    }
  }
}
