package io.algopilot.market.observation;

import io.algopilot.market.scanner.MarketScanStore;
import io.algopilot.market.scanner.ScanResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {
  private final MarketObservationService observationService;
  private final MarketScanStore scanStore;

  public MarketController(MarketObservationService observationService, MarketScanStore scanStore) {
    this.observationService = observationService;
    this.scanStore = scanStore;
  }

  @GetMapping("/observations")
  public ResponseEntity<List<MarketObservation>> getAllLatestObservations() {
    return ResponseEntity.ok(observationService.getAllLatest());
  }

  @GetMapping("/observations/{symbol}")
  public ResponseEntity<List<MarketObservation>> getRecentObservations(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(observationService.getRecent(symbol, limit));
  }

  @GetMapping("/latest/{symbol}")
  public ResponseEntity<MarketObservation> getLatestObservation(@PathVariable String symbol) {
    return observationService.getLatest(symbol)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/scans")
  public ResponseEntity<List<ScanResult>> getRecentScans(
      @RequestParam(required = false) String symbol,
      @RequestParam(defaultValue = "50") int limit) {
    if (symbol != null && !symbol.isBlank()) {
      return ResponseEntity.ok(scanStore.findRecentBySymbol(symbol, limit));
    }
    return ResponseEntity.ok(scanStore.findRecent(limit));
  }

  @GetMapping("/scans/{id}")
  public ResponseEntity<ScanResult> getScanById(@PathVariable UUID id) {
    return scanStore.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  public record IngestObservationRequest(
      String symbol,
      String provider,
      String environment,
      BigDecimal lastPrice,
      BigDecimal bid,
      BigDecimal ask,
      BigDecimal volume,
      BigDecimal openPrice,
      BigDecimal highPrice,
      BigDecimal lowPrice,
      BigDecimal closePrice,
      String timeframe,
      Instant marketTimestamp
  ) {}

  @PostMapping("/observations")
  public ResponseEntity<MarketObservation> ingestObservation(@RequestBody IngestObservationRequest req) {
    Instant now = Instant.now();
    MarketObservation obs = MarketObservation.create(
        UUID.randomUUID(),
        req.symbol(),
        req.provider(),
        req.environment(),
        req.lastPrice(),
        req.bid(),
        req.ask(),
        req.volume(),
        req.openPrice(),
        req.highPrice(),
        req.lowPrice(),
        req.closePrice(),
        req.timeframe(),
        req.marketTimestamp() != null ? req.marketTimestamp() : now,
        now,
        MarketObservationService.DEFAULT_STALE_THRESHOLD_MS
    );
    return ResponseEntity.ok(observationService.recordObservation(obs));
  }

  @PostMapping("/observations/batch")
  public ResponseEntity<List<MarketObservation>> ingestObservationsBatch(@RequestBody List<IngestObservationRequest> requests) {
    Instant now = Instant.now();
    List<MarketObservation> recorded = requests.stream().map(req -> {
      MarketObservation obs = MarketObservation.create(
          UUID.randomUUID(),
          req.symbol(),
          req.provider(),
          req.environment(),
          req.lastPrice(),
          req.bid(),
          req.ask(),
          req.volume(),
          req.openPrice(),
          req.highPrice(),
          req.lowPrice(),
          req.closePrice(),
          req.timeframe(),
          req.marketTimestamp() != null ? req.marketTimestamp() : now,
          now,
          MarketObservationService.DEFAULT_STALE_THRESHOLD_MS
      );
      return observationService.recordObservation(obs);
    }).toList();
    return ResponseEntity.ok(recorded);
  }
}
