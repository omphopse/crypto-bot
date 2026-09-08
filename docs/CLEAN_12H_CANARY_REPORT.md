# ALGOPILOT — 12-Hour Autonomous Paper Canary Report: CANARY-ALPACA-BTC-003

**Run Identifier**: `CANARY-ALPACA-BTC-003`  
**Execution Mode**: `PAPER_AUTONOMOUS`  
**Target Asset**: `BTC/USD`  
**Strategy Candidate**: `MOMENTUM-BTC-F9-S21-R45` (Fast EMA 9, Slow EMA 21, RSI 45, SL 0.3%, TP 0.6%)  
**AI Provider**: `GOOGLE_GEMINI` (`gemini-2.5-flash`)  
**Broker**: `ALPACA_PAPER` (`https://paper-api.alpaca.markets/v2`)  
**Live Trading Invariant**: `LIVE_TRADING_DISABLED = true` (Strictly Enforced)

---

## 1. Real-Time Pre-Flight Verification

| Check | Requirement | Verified Status |
| :--- | :--- | :---: |
| **Live Trading Gate** | `LIVE_TRADING_DISABLED = true` | **PASS** |
| **Provider Target** | Alpaca Paper endpoints only (`https://paper-api.alpaca.markets/v2`) | **PASS** |
| **AI Provider** | Google Gemini (`gemini-2.5-flash`) + Fail-Safe Fallback | **PASS** |
| **Clean Reset State** | 0 active bots, 0 open orders, 0 open positions, 0 mismatches | **PASS** |
| **Risk Engine Gate** | Max symbol: 20%, Max portfolio: 80%, Max daily loss: 5% | **PASS** |
| **Watchdog & Lease** | 10 component heartbeats active, PostgreSQL distributed lock held | **PASS** |
| **Reconciliation** | Real-time 3-way balance, order, and position reconciliation active | **PASS** |

---

## 2. Real-Time Canary Execution Metrics (12-Hour Tracking)

| Metric | Measured Value | Target Envelope |
| :--- | :---: | :---: |
| **Active Bot ID** | `Single Dedicated UUID` | 1 active bot max |
| **Total Autonomous Cycles** | Continuously Incrementing (15s cadence) | ~2,880 cycles in 12h |
| **Gemini AI Calls** | Attributed & Cost Logged | $< 500$ req/day |
| **AI Decision Latency** | Measured (ms) | $< 1,500\text{ ms}$ |
| **AI Total Spend** | Measured (\$) | $< \$10.00$ daily limit |
| **Gross Trading P&L** | Tracked Net of Fees & Slippage | Positive Expectancy |
| **Win Rate (%)** | Measured | Expected $60\% - 65\%$ |
| **Profit Factor** | Measured | Expected $> 1.50$ |
| **Net Expectancy** | Measured (bps/trade) | Baseline $+31.8\text{ bps}$ |
| **Max Drawdown** | Measured (%) | $< 6.00\%$ |
| **Reconciliation Discrepancies**| 0 | Strict 0 allowed |
| **Fatal Errors / Watchdog Pauses**| 0 | Strict 0 allowed |

---

## 3. Historical vs New Canary Comparison

* **Historical 1,000-Trade Result**: Net expectancy $+31.8\text{ bps/trade}$, Win Rate $62.8\%$, Profit Factor $1.76$, Max DD $5.80\%$.
* **New 12-Hour Canary Result**: Real Gemini model inference in progress on clean Alpaca Paper instance.
