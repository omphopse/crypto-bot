# Strategy Validation Report: 001

## 1. Strategy Identification & Invariants
- **Candidate Name**: `MOMENTUM-BTC-F9-S21-R45`
- **Family**: `MOMENTUM`
- **Target Asset / Timeframe**: `BTC/USD` (1-hour candles)
- **Version Number**: `v1` (Immutable definition)
- **Strategy Parameters**:
  - `fastEma`: 9
  - `slowEma`: 21
  - `rsiThreshold`: 45
  - `stopLossPct`: 0.003 (0.3%)
  - `takeProfitPct`: 0.005 (0.5%)

---

## 2. Research Baseline vs Live Extended Paper Results
| Dimension | In-Sample / Backtest Research | Extended Paper Canary (1,000 Trades) | Drift / Evaluation |
| :--- | :--- | :--- | :--- |
| **Trade Sample Size** | 200 synthetic / 500 historical | **1,000 real paper executions** | Highly representative |
| **Win Rate** | 65.0% | **62.80%** (`[59.8%, 65.8%]` 95% CI) | In-line (-2.2%) |
| **Profit Factor** | 1.85 | **1.76** | Robust |
| **Gross Expectancy** | +48.0 bps | **+42.0 bps** | Consistent |
| **Net Expectancy (after costs)**| **+35.0 bps** | **+31.8 bps** (`[+24.5, +39.1]` 95% CI)| **-3.2 bps (-9.1% relative)** |
| **Max Drawdown** | 5.20% | **5.80%** | Controlled |
| **Sharpe / Sortino** | 1.95 / 2.60 | **1.82 / 2.45** | High-quality risk-adjusted returns |

---

## 3. Sequential Block Consistency (Stability Check)
The 1,000-trade evaluation was divided into 10 consecutive blocks of 100 trades each.
- **Minimum Block Win Rate**: 60.0% (Block 7)
- **Maximum Block Win Rate**: 66.0% (Block 10)
- **Minimum Block Net Expectancy**: +28.4 bps (Block 7)
- **Maximum Block Net Expectancy**: +35.5 bps (Block 10)
- **Conclusion**: The positive edge is persistent across the entire operational timeline without single-event dependency or clustering.

---

## 4. Friction Stress & Failsafe Gating
- **1.0x Baseline Costs**: +31.8 bps net expectancy
- **1.5x Elevated Costs**: +24.5 bps net expectancy
- **2.0x Double Costs**: +17.2 bps net expectancy
- **3.0x Triple Costs**: +2.6 bps net expectancy
- **Assessment**: The strategy maintains positive economic returns even under severe transaction friction.

---

## 5. Conclusion & Qualification Status
- **Final Classification**: `POSITIVE SAMPLE`
- **Paper Qualification**: `PAPER_QUALIFIED`
- **Live Trading Status**: `LIVE_TRADING_DISABLED` (Locked)
- **Summary**: `MOMENTUM-BTC-F9-S21-R45` (v1) satisfies all deterministic statistical, cost-stress, and execution stability criteria for paper certification.
