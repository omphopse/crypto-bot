package io.algopilot.market.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.model.Candle;
import io.algopilot.market.observation.MarketObservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketScannerTest {
  private MemoryMarketScanStore store;
  private AuditEventWriter audit;
  private Clock clock;
  private MarketScanner scanner;

  @BeforeEach
  void setUp() {
    store = new MemoryMarketScanStore();
    audit = mock(AuditEventWriter.class);
    clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
    scanner = new MarketScanner(store, audit, clock);
  }

  @Test
  void testInsufficientHistory_producesColdSnapshotAndNoCandidate() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> shortHistory = generateCandles(10, new BigDecimal("100.00"), BigDecimal.ZERO);
    MarketObservation obs = createObservation("BTC/USD", new BigDecimal("100.00"), false);

    ScanResult result = scanner.scan(sessionId, botId, shortHistory, obs);

    assertThat(result.candidateType()).isEqualTo(CandidateType.NO_CANDIDATE);
    assertThat(result.status()).isEqualTo("NO_CANDIDATE");
    assertThat(result.reason()).contains("Insufficient candle history");
    assertThat(result.indicatorSnapshot().isWarmedUp()).isFalse();
  }

  @Test
  void testStaleObservation_abortsScanWithSkippedStaleData() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> history = generateCandles(30, new BigDecimal("100.00"), BigDecimal.ZERO);
    MarketObservation staleObs = createObservation("ETH/USD", new BigDecimal("100.00"), true);

    ScanResult result = scanner.scan(sessionId, botId, history, staleObs);

    assertThat(result.candidateType()).isEqualTo(CandidateType.NO_CANDIDATE);
    assertThat(result.status()).isEqualTo("SKIPPED_STALE_DATA");
    assertThat(result.triggerConditions()).contains("MARKET_DATA_STALE");
  }

  @Test
  void testMomentumCandidate_detectedWhenEmaCrossedAndRsiBullish() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    // Strong upward trend
    List<Candle> uptrend = generateCandles(30, new BigDecimal("100.00"), new BigDecimal("1.50"));
    MarketObservation obs = createObservation("NVDA", uptrend.get(uptrend.size() - 1).close(), false);

    ScanResult result = scanner.scan(sessionId, botId, uptrend, obs);

    assertThat(result.candidateType()).isEqualTo(CandidateType.MOMENTUM);
    assertThat(result.status()).isEqualTo("CANDIDATE_DETECTED");
    assertThat(result.confidenceScore()).isGreaterThan(BigDecimal.ZERO);
    assertThat(result.triggerConditions()).contains("EMA9_ABOVE_EMA21");
    verify(audit, times(1)).record(eq("SCANNER"), eq("NVDA"), eq("SCAN_CANDIDATE_DETECTED"), eq("SCAN_RESULT"), eq(result.id().toString()), anyMap());
  }

  @Test
  void testMomentumCandidate_detectedWithCustomEmaParameters() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> uptrend = generateCandles(35, new BigDecimal("100.00"), new BigDecimal("1.50"));
    MarketObservation obs = createObservation("BTC/USD", uptrend.get(uptrend.size() - 1).close(), false);

    ScanResult result = scanner.scan(sessionId, botId, uptrend, obs, 12, 26, 14);

    assertThat(result.candidateType()).isEqualTo(CandidateType.MOMENTUM);
    assertThat(result.status()).isEqualTo("CANDIDATE_DETECTED");
    assertThat(result.confidenceScore()).isGreaterThan(BigDecimal.ZERO);
    assertThat(result.triggerConditions()).contains("EMA12_ABOVE_EMA26");
    assertThat(result.indicatorSnapshot().isWarmedUp()).isTrue();
    assertThat(result.indicatorSnapshot().emaFast()).isGreaterThan(result.indicatorSnapshot().emaSlow());
  }

  @Test
  void testOversoldCandidate_detectedWhenRsiBelow30() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    // Strong downward selloff
    List<Candle> selloff = generateCandles(30, new BigDecimal("200.00"), new BigDecimal("-3.00"));
    MarketObservation obs = createObservation("TSLA", selloff.get(selloff.size() - 1).close(), false);

    ScanResult result = scanner.scan(sessionId, botId, selloff, obs);

    assertThat(result.candidateType()).isIn(CandidateType.OVERSOLD, CandidateType.MEAN_REVERSION);
    assertThat(result.status()).isEqualTo("CANDIDATE_DETECTED");
    assertThat(result.indicatorSnapshot().rsi14()).isLessThan(new BigDecimal("35.00"));
  }

  @Test
  void testBreakoutCandidate_detectedWhenPriceExceeds20BarHighWithVolume() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> base = generateCandles(25, new BigDecimal("100.00"), BigDecimal.ZERO);
    // 26th bar breaks out strongly with 2x volume
    Candle breakout = new Candle("AAPL", "1m", new BigDecimal("100.00"), new BigDecimal("115.00"), new BigDecimal("99.00"), new BigDecimal("114.00"), new BigDecimal("2500"), Instant.now());
    base.add(breakout);
    MarketObservation obs = createObservation("AAPL", new BigDecimal("114.00"), false);

    ScanResult result = scanner.scan(sessionId, botId, base, obs);

    assertThat(result.candidateType()).isIn(CandidateType.BREAKOUT, CandidateType.MOMENTUM, CandidateType.VOLUME_ANOMALY);
    assertThat(result.status()).isEqualTo("CANDIDATE_DETECTED");
  }

  @Test
  void testVolumeAnomaly_detectedWhenVolumeSpikes2Point5x() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> base = generateCandles(25, new BigDecimal("100.00"), BigDecimal.ZERO);
    // 26th bar has 4x volume without breakout
    Candle volumeSpike = new Candle("SOL/USD", "1m", new BigDecimal("100.00"), new BigDecimal("100.50"), new BigDecimal("99.50"), new BigDecimal("100.00"), new BigDecimal("4500"), Instant.now());
    base.add(volumeSpike);
    MarketObservation obs = createObservation("SOL/USD", new BigDecimal("100.00"), false);

    ScanResult result = scanner.scan(sessionId, botId, base, obs);

    assertThat(result.candidateType()).isEqualTo(CandidateType.VOLUME_ANOMALY);
    assertThat(result.triggerConditions()).contains("VOLUME_SPIKE_2.5X");
  }

  @Test
  void testQuietMarket_producesNoCandidate() {
    UUID sessionId = UUID.randomUUID();
    UUID botId = UUID.randomUUID();
    List<Candle> flat = generateCandles(30, new BigDecimal("100.00"), BigDecimal.ZERO);
    MarketObservation obs = createObservation("BTC/USD", new BigDecimal("100.00"), false);

    ScanResult result = scanner.scan(sessionId, botId, flat, obs);

    assertThat(result.candidateType()).isEqualTo(CandidateType.NO_CANDIDATE);
    assertThat(result.status()).isEqualTo("NO_CANDIDATE");
    verify(audit, times(1)).record(eq("SCANNER"), eq("BTC/USD"), eq("SCAN_COMPLETED"), eq("SCAN_RESULT"), eq(result.id().toString()), anyMap());
  }

  private List<Candle> generateCandles(int count, BigDecimal startPrice, BigDecimal priceStep) {
    List<Candle> candles = new ArrayList<>(count);
    BigDecimal price = startPrice;
    Instant time = clock.instant().minusSeconds(count * 60L);
    for (int i = 0; i < count; i++) {
      BigDecimal next = price.add(priceStep);
      BigDecimal high = next.max(price).add(new BigDecimal("0.50"));
      BigDecimal low = next.min(price).subtract(new BigDecimal("0.50"));
      candles.add(new Candle("TEST", "1m", price, high, low, next, new BigDecimal("1000"), time.plusSeconds(i * 60L)));
      price = next;
    }
    return candles;
  }

  private MarketObservation createObservation(String symbol, BigDecimal price, boolean isStale) {
    Instant now = clock.instant();
    Instant marketTime = isStale ? now.minusSeconds(120) : now.minusSeconds(5);
    return MarketObservation.create(
        UUID.randomUUID(), symbol, "ALPACA_PAPER", "PAPER",
        price, price.subtract(new BigDecimal("0.05")), price.add(new BigDecimal("0.05")),
        new BigDecimal("1000"), price, price.add(new BigDecimal("0.50")), price.subtract(new BigDecimal("0.50")), price,
        "1m", marketTime, now, 60_000L
    );
  }

  private static final class MemoryMarketScanStore implements MarketScanStore {
    private final List<ScanResult> results = Collections.synchronizedList(new ArrayList<>());

    @Override
    public ScanResult save(ScanResult r) {
      results.add(0, r);
      return r;
    }

    @Override
    public List<ScanResult> findRecent(int limit) {
      return results.subList(0, Math.min(results.size(), limit));
    }

    @Override
    public List<ScanResult> findRecentBySymbol(String symbol, int limit) {
      return results.stream().filter(r -> r.symbol().equalsIgnoreCase(symbol)).limit(limit).toList();
    }

    @Override
    public Optional<ScanResult> findById(UUID id) {
      return results.stream().filter(r -> r.id().equals(id)).findFirst();
    }
  }
}
