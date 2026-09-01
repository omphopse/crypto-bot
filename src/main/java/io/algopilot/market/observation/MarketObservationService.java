package io.algopilot.market.observation;

import io.algopilot.audit.AuditEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MarketObservationService {
  private static final Logger log = LoggerFactory.getLogger(MarketObservationService.class);
  public static final long DEFAULT_STALE_THRESHOLD_MS = 60_000L; // 1 minute default freshness

  private final MarketDataStore store;
  private final AuditEventWriter audit;
  private final Clock clock;
  private final long staleThresholdMs;

  @Autowired
  public MarketObservationService(MarketDataStore store, AuditEventWriter audit, Clock clock) {
    this(store, audit, clock, DEFAULT_STALE_THRESHOLD_MS);
  }

  public MarketObservationService(MarketDataStore store, AuditEventWriter audit) {
    this(store, audit, Clock.systemUTC(), DEFAULT_STALE_THRESHOLD_MS);
  }

  public MarketObservationService(MarketDataStore store, AuditEventWriter audit, Clock clock, long staleThresholdMs) {
    this.store = store;
    this.audit = audit;
    this.clock = clock;
    this.staleThresholdMs = staleThresholdMs;
  }

  public MarketObservation recordObservation(MarketObservation observation) {
    if (observation == null) throw new IllegalArgumentException("Observation cannot be null");

    if (observation.validationStatus().startsWith("INVALID:")) {
      log.warn("Market observation rejected for {}: {}", observation.symbol(), observation.validationStatus());
      audit.record("MARKET", observation.symbol(), "MARKET_OBSERVATION_REJECTED", "OBSERVATION",
          observation.id().toString(), Map.of("reason", observation.validationStatus(), "price", observation.lastPrice().toString()));
      return observation;
    }

    if (observation.isStale()) {
      log.warn("Market observation stale for {}: freshnessMs={}", observation.symbol(), observation.freshnessMs());
      audit.record("MARKET", observation.symbol(), "MARKET_DATA_STALE", "OBSERVATION",
          observation.id().toString(), Map.of("freshnessMs", String.valueOf(observation.freshnessMs())));
    }

    store.save(observation);
    audit.record("MARKET", observation.symbol(), "MARKET_OBSERVATION_RECEIVED", "OBSERVATION",
        observation.id().toString(), Map.of(
            "provider", observation.provider(),
            "lastPrice", observation.lastPrice().toString(),
            "freshnessMs", String.valueOf(observation.freshnessMs()),
            "status", observation.validationStatus()
        ));

    return observation;
  }

  public Optional<MarketObservation> getLatest(String symbol) {
    return store.findLatest(symbol);
  }

  public List<MarketObservation> getRecent(String symbol, int limit) {
    return store.findRecent(symbol, limit);
  }

  public List<MarketObservation> getAllLatest() {
    return store.findAllLatest();
  }
}
