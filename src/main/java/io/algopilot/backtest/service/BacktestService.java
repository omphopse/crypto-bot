package io.algopilot.backtest.service;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.engine.BacktestEngine;
import io.algopilot.backtest.engine.WalkForwardEngine;
import io.algopilot.backtest.model.BacktestRequest;
import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.Candle;
import io.algopilot.backtest.model.WalkForwardRequest;
import io.algopilot.backtest.model.WalkForwardResult;
import io.algopilot.backtest.persistence.BacktestStore;
import io.algopilot.strategy.StrategyStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BacktestService {
  private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

  private final BacktestEngine backtestEngine;
  private final WalkForwardEngine walkForwardEngine;
  private final BacktestStore backtestStore;
  private final StrategyStore strategyStore;
  private final AuditEventWriter audit;

  public BacktestService(
      BacktestEngine backtestEngine,
      WalkForwardEngine walkForwardEngine,
      BacktestStore backtestStore,
      StrategyStore strategyStore,
      AuditEventWriter audit) {
    this.backtestEngine = backtestEngine;
    this.walkForwardEngine = walkForwardEngine;
    this.backtestStore = backtestStore;
    this.strategyStore = strategyStore;
    this.audit = audit;
  }

  @Transactional
  public BacktestResult runBacktest(BacktestRequest request, List<Candle> candleData) {
    if (request.strategyVersionId() != null) {
      strategyStore.findVersionById(request.strategyVersionId())
          .orElseThrow(() -> new IllegalArgumentException("STRATEGY_VERSION_NOT_FOUND:" + request.strategyVersionId()));
    }

    log.info("STARTING_BACKTEST for strategyVersionId={} symbol={} timeframe={}", request.strategyVersionId(), request.symbol(), request.timeframe());
    BacktestResult result = backtestEngine.run(request, candleData);
    backtestStore.saveBacktest(result);

    audit.record(
        "BACKTEST",
        request.strategyVersionId() != null ? request.strategyVersionId().toString() : "SYSTEM",
        "BACKTEST_EXECUTED",
        "BACKTEST",
        result.id().toString(),
        Map.of(
            "symbol", result.symbol(),
            "timeframe", result.timeframe(),
            "totalReturnPct", result.totalReturnPct().toPlainString(),
            "sharpeRatio", result.sharpeRatio().toPlainString(),
            "maxDrawdownPct", result.maxDrawdownPct().toPlainString(),
            "totalTrades", result.totalTrades()
        )
    );

    return result;
  }

  @Transactional
  public WalkForwardResult runWalkForward(WalkForwardRequest request, List<Candle> candleData) {
    if (request.strategyVersionId() != null) {
      strategyStore.findVersionById(request.strategyVersionId())
          .orElseThrow(() -> new IllegalArgumentException("STRATEGY_VERSION_NOT_FOUND:" + request.strategyVersionId()));
    }

    log.info("STARTING_WALK_FORWARD for strategyVersionId={} symbol={} windows={}", request.strategyVersionId(), request.symbol(), request.windowCount());
    WalkForwardResult result = walkForwardEngine.run(request, candleData);
    backtestStore.saveWalkForward(result);

    audit.record(
        "BACKTEST",
        request.strategyVersionId() != null ? request.strategyVersionId().toString() : "SYSTEM",
        "WALK_FORWARD_EXECUTED",
        "WALK_FORWARD",
        result.id().toString(),
        Map.of(
            "symbol", result.symbol(),
            "timeframe", result.timeframe(),
            "windowCount", result.windowCount(),
            "avgOosEfficiency", result.avgOosEfficiency().toPlainString()
        )
    );

    return result;
  }

  public Optional<BacktestResult> getBacktest(UUID id) {
    return backtestStore.findBacktestById(id);
  }

  public List<BacktestResult> getRecentBacktests(int limit) {
    return backtestStore.findRecentBacktests(limit);
  }

  public Optional<WalkForwardResult> getWalkForward(UUID id) {
    return backtestStore.findWalkForwardById(id);
  }
}
