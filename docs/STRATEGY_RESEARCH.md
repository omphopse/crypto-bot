# Strategy Research, Validation & Economic Edge Engine

## 1. Overview
The Strategy Research subsystem provides an authoritative, reproducible environment for evaluating quantitative strategies under realistic market frictions and cost assumptions.

---

## 2. Core Capabilities
1. **Immutable Experiments (`StrategyExperiment`)**:
   - Captures strategy version, symbol, timeframe, initial capital, and explicit cost models (slippage, maker/taker fees, spread, latency).
2. **Realistic Cost Modeling**:
   - Simulates maker/taker commission, bid/ask spread, and dynamic slippage per trade.
   - Calculates **Net Expectancy**:
     $$\text{NetExpectancy} = (\text{WinRate} \times \text{AvgWin}) - (\text{LossRate} \times \text{AvgLoss}) - \text{AvgTradingCostPerTrade}$$
3. **Walk-Forward Validation**:
   - Rolling in-sample (train) and out-of-sample (test) windows to detect overfitting and out-of-sample degradation.
4. **Parameter Sensitivity Sweeps**:
   - Evaluates indicator parameters across a continuous grid to differentiate between robust plateaus and fragile overfit spikes.
5. **Market Regime Segmentation**:
   - Evaluates performance across `BULL_TREND`, `BEAR_TREND`, `SIDEWAYS`, `HIGH_VOLATILITY`, and `LOW_VOLATILITY`.
6. **Strategy Health & Drift Monitoring**:
   - Compares backtest expectations against actual paper and demo execution metrics. Flags `STRATEGY_DEGRADATION` if performance drifts beyond acceptable limits.
