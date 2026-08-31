# Test report

## 2026-08-31 — Backtesting & Walk-Forward Validation Engine Milestone

Command: `mvn test -q`

Result: passed (89 tests executed across 27 test classes, 0 failures, 0 errors, 0 skipped).

### Covered Backtesting & Quantitative Validation Scenarios:

1. **Indicator Calculations (`Indicators`):**
   - Simple Moving Average (SMA) accuracy across multi-period windows.
   - Exponential Moving Average (EMA) calculation with smoothing multiplier.
   - Relative Strength Index (RSI) bounds checking and Wilder smoothing accuracy.
   - Average True Range (ATR) true range volatility measurement.
   - Safe list sizing for undersized candle series.

2. **Performance Metrics (`PerformanceMetricsCalculator`):**
   - Exact peak-to-trough Maximum Drawdown percentage computation.
   - Annualized Sharpe Ratio using daily return variances and risk-free rate adjustment.
   - Gross profit to gross loss Profit Factor calculation.
   - Win Rate percentage based on realized trade P&L.

3. **Event-Driven Backtesting Simulation (`BacktestEngine`):**
   - Bar-by-bar chronological execution.
   - Strategy entry and exit trigger evaluation against technical indicators.
   - Realistic friction deduction: configurable basis point slippage and broker fee deduction.
   - Marked-to-market equity curve time series tracking.
   - Trade journaling with entry/exit prices, fees, net P&L, and reason codes.

4. **Walk-Forward Validation (`WalkForwardEngine`):**
   - Chronological dataset segmentation into sequential In-Sample and Out-Of-Sample windows.
   - Parameter optimization simulation across windows.
   - Walk-Forward Efficiency (WFE) ratio calculation.

5. **Persistence & REST API:**
   - PostgreSQL persistence of backtest runs, trade records, and walk-forward evaluations via `JdbcBacktestStore`.
   - `BacktestController` endpoints (`/api/backtests/run`, `/{id}`, `/walk-forward`, `/walk-forward/{id}`).

### Earlier Verified Milestone Suites (All Passing):
- Exchange Adapters (Alpaca Paper & Bybit Demo) and Execution Gateway (21 tests).
- Reconciliation & Recovery engine, service, controller, and health indicators (54 tests).
- Deterministic Risk Engine evaluation.
- Idempotent order store and lifecycle state machine.
- Fill ingestion and position accounting.
- Bot operational controls and emergency stop guard.
- Strategy immutability and versioning.
- Typed agent decision journaling.
