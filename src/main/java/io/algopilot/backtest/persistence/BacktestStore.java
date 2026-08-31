package io.algopilot.backtest.persistence;

import io.algopilot.backtest.model.BacktestResult;
import io.algopilot.backtest.model.WalkForwardResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BacktestStore {
  void saveBacktest(BacktestResult result);

  Optional<BacktestResult> findBacktestById(UUID id);

  List<BacktestResult> findBacktestsByStrategyId(UUID strategyVersionId);

  List<BacktestResult> findRecentBacktests(int limit);

  void saveWalkForward(WalkForwardResult result);

  Optional<WalkForwardResult> findWalkForwardById(UUID id);
}
