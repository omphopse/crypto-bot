package io.algopilot.backtest.controller;

import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardRequest;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.service.BacktestService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtests")
public class BacktestController {
  private final BacktestService backtestService;

  public record BacktestPayload(
      UUID strategyVersionId,
      String symbol,
      String timeframe,
      Instant startTime,
      Instant endTime,
      BigDecimal initialCapital,
      Integer slippageBps,
      Integer feeBps,
      List<Candle> candleData
  ) {}

  public record WalkForwardPayload(
      UUID strategyVersionId,
      String symbol,
      String timeframe,
      int windowCount,
      int inSampleDays,
      int outOfSampleDays,
      BigDecimal initialCapital,
      List<Candle> candleData
  ) {}

  public BacktestController(BacktestService backtestService) {
    this.backtestService = backtestService;
  }

  @PostMapping("/run")
  public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestPayload payload) {
    BacktestRequest request = new BacktestRequest(
        payload.strategyVersionId(),
        payload.symbol() != null ? payload.symbol() : "BTC/USD",
        payload.timeframe() != null ? payload.timeframe() : "1h",
        payload.startTime() != null ? payload.startTime() : Instant.now().minus(30, ChronoUnit.DAYS),
        payload.endTime() != null ? payload.endTime() : Instant.now(),
        payload.initialCapital() != null ? payload.initialCapital() : new BigDecimal("100000.00"),
        payload.slippageBps() != null ? payload.slippageBps() : 5,
        payload.feeBps() != null ? payload.feeBps() : 10
    );

    List<Candle> candles = payload.candleData() != null && !payload.candleData().isEmpty()
        ? payload.candleData()
        : generateSyntheticCandles(request.symbol(), request.timeframe(), 200);

    BacktestResult result = backtestService.runBacktest(request, candles);
    return ResponseEntity.ok(result);
  }

  @GetMapping
  public List<BacktestResult> listRecentBacktests(@RequestParam(defaultValue = "10") int limit) {
    return backtestService.getRecentBacktests(limit);
  }

  @GetMapping("/{id}")
  public ResponseEntity<BacktestResult> getBacktest(@PathVariable UUID id) {
    return backtestService.getBacktest(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/walk-forward")
  public ResponseEntity<WalkForwardResult> runWalkForward(@RequestBody WalkForwardPayload payload) {
    WalkForwardRequest request = new WalkForwardRequest(
        payload.strategyVersionId(),
        payload.symbol() != null ? payload.symbol() : "BTC/USD",
        payload.timeframe() != null ? payload.timeframe() : "1h",
        payload.windowCount() > 0 ? payload.windowCount() : 5,
        payload.inSampleDays() > 0 ? payload.inSampleDays() : 20,
        payload.outOfSampleDays() > 0 ? payload.outOfSampleDays() : 10,
        payload.initialCapital() != null ? payload.initialCapital() : new BigDecimal("100000.00")
    );

    List<Candle> candles = payload.candleData() != null && !payload.candleData().isEmpty()
        ? payload.candleData()
        : generateSyntheticCandles(request.symbol(), request.timeframe(), 300);

    WalkForwardResult result = backtestService.runWalkForward(request, candles);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/walk-forward/{id}")
  public ResponseEntity<WalkForwardResult> getWalkForward(@PathVariable UUID id) {
    return backtestService.getWalkForward(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleBadRequest(IllegalArgumentException e) {
    return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", e.getMessage()));
  }

  private List<Candle> generateSyntheticCandles(String symbol, String timeframe, int count) {
    List<Candle> list = new ArrayList<>(count);
    BigDecimal current = new BigDecimal("60000.00");
    Instant now = Instant.now().minus(count, ChronoUnit.HOURS);

    for (int i = 0; i < count; i++) {
      double pctChange = Math.sin(i / 10.0) * 0.015 + 0.002;
      BigDecimal next = current.multiply(BigDecimal.valueOf(1.0 + pctChange));
      BigDecimal high = current.max(next).multiply(BigDecimal.valueOf(1.002));
      BigDecimal low = current.min(next).multiply(BigDecimal.valueOf(0.998));
      BigDecimal vol = BigDecimal.valueOf(10.5 + Math.abs(pctChange) * 100);

      list.add(new Candle(symbol, timeframe, current, high, low, next, vol, now.plus(i, ChronoUnit.HOURS)));
      current = next;
    }
    return list;
  }
}
