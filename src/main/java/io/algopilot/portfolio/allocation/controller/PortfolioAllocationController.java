package io.algopilot.portfolio.allocation.controller;

import io.algopilot.backtest.model.Candle;
import io.algopilot.portfolio.allocation.model.PortfolioAllocationPlan;
import io.algopilot.portfolio.allocation.service.PortfolioAllocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/portfolio/allocation")
public class PortfolioAllocationController {
  private final PortfolioAllocationService service;

  public record AllocatePayload(
      List<String> symbols,
      BigDecimal totalCapital,
      Map<String, List<Candle>> historicalData
  ) {}

  public PortfolioAllocationController(PortfolioAllocationService service) {
    this.service = service;
  }

  @PostMapping({"/allocate", "/generate"})
  public ResponseEntity<PortfolioAllocationPlan> allocate(@RequestBody(required = false) AllocatePayload payload) {
    if (payload == null) payload = new AllocatePayload(null, null, null);
    List<String> symbols = payload.symbols() != null && !payload.symbols().isEmpty()
        ? payload.symbols()
        : List.of("BTC/USD", "ETH/USD", "SOL/USD");

    BigDecimal capital = payload.totalCapital() != null ? payload.totalCapital() : new BigDecimal("100000.00");

    Map<String, List<Candle>> data = payload.historicalData() != null && !payload.historicalData().isEmpty()
        ? payload.historicalData()
        : generateSyntheticDataset(symbols, 100);

    PortfolioAllocationPlan plan = service.generateAllocationPlan(symbols, capital, data);
    return ResponseEntity.ok(plan);
  }

  @GetMapping("/latest")
  public ResponseEntity<PortfolioAllocationPlan> getLatest() {
    return service.getLatestPlan()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}")
  public ResponseEntity<PortfolioAllocationPlan> getById(@PathVariable UUID id) {
    return service.getPlan(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/history")
  public List<PortfolioAllocationPlan> listHistory(@RequestParam(defaultValue = "10") int limit) {
    return service.getRecentPlans(limit);
  }

  private Map<String, List<Candle>> generateSyntheticDataset(List<String> symbols, int count) {
    Map<String, List<Candle>> map = new HashMap<>();
    Instant now = Instant.now().minus(count, ChronoUnit.HOURS);

    for (int sIdx = 0; sIdx < symbols.size(); sIdx++) {
      String sym = symbols.get(sIdx);
      List<Candle> list = new ArrayList<>(count);
      BigDecimal basePrice = BigDecimal.valueOf(100.0 * Math.pow(10, sIdx));
      BigDecimal current = basePrice;
      double volMultiplier = 0.01 + sIdx * 0.01;

      for (int i = 0; i < count; i++) {
        double pct = Math.sin((i + sIdx * 3) / 6.0) * volMultiplier;
        BigDecimal next = current.multiply(BigDecimal.valueOf(1.0 + pct));
        BigDecimal high = current.max(next).multiply(BigDecimal.valueOf(1.002));
        BigDecimal low = current.min(next).multiply(BigDecimal.valueOf(0.998));
        BigDecimal vol = BigDecimal.valueOf(100.0 + i);

        list.add(new Candle(sym, "1h", current, high, low, next, vol, now.plus(i, ChronoUnit.HOURS)));
        current = next;
      }
      map.put(sym, list);
    }
    return map;
  }
}
