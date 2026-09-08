# ALGOPILOT — Phase M Real-Model Validation Plan

**Document Date**: September 8, 2026  
**Execution Objective**: Autonomous 12-Hour Alpaca Paper Canary with Real Google Gemini AI Provider.  
**Strategy**: `MOMENTUM-BTC-F9-S21-R45` (Immutable Version)  
**Execution Environment**: `ALPACA_PAPER` ONLY (`https://paper-api.alpaca.markets/v2`)  
**Live Trading Invariant**: `LIVE_TRADING_DISABLED = true` (Strictly Enforced)

---

## 1. Validation Run Specification

| Parameter | Specification | Invariant / Enforcement |
| :--- | :--- | :--- |
| **Canary Run Name** | `Canary-BTC-Overnight` | Single active bot deployment |
| **Target Asset** | `BTC/USD` | Continuous 24/7 paper trading |
| **Strategy Candidate** | `MOMENTUM-BTC-F9-S21-R45` | Fast EMA: 9, Slow EMA: 21, RSI: 45 |
| **Risk Parameters** | Stop Loss: 0.3%, Take Profit: 0.6% | Strict limit order validation |
| **AI Provider** | `GOOGLE_GEMINI` (`gemini-2.5-flash`) | Real structured LLM reasoning |
| **AI Fallback** | `FakeLLMDecisionProvider` | Automatic fail-safe on connection error |
| **Broker Adapter** | `AlpacaPaperAdapter` | `paper-api.alpaca.markets/v2` |
| **Execution Mode** | `PAPER_AUTONOMOUS` | Background scheduler driven |
| **Run Duration** | 12 Hours (Continuous) | Zero manual per-order triggers |
| **Initial Capital** | \$100,000.00 (Paper Equity) | Real-time mark-to-market accounting |

---

## 2. End-to-End Forensic Traceability Chain

Every executed order during the 12-hour canary must maintain an unbroken, verifiable causal chain:
$$\text{Market Observation} \longrightarrow \text{Scanner} \longrightarrow \text{Research Evidence} \longrightarrow \text{TradingContext (SHA-256)} \longrightarrow \text{Gemini Decision} \longrightarrow \text{Strategy Validation} \longrightarrow \text{RiskEngine Gate} \longrightarrow \text{OrderRecord} \longrightarrow \text{Alpaca Order ID} \longrightarrow \text{Broker Fill} \longrightarrow \text{Position Lifecycle} \longrightarrow \text{Dynamic Exit} \longrightarrow \text{Reconciliation} \longrightarrow \text{Append-Only Audit}$$

---

## 3. Pre-Flight Safety Verification Checklist

1. [x] **Live Trading Lock**: `LIVE_TRADING_DISABLED = true` verified in all gateway services.
2. [x] **Alpaca URL Guard**: `AlpacaConfig.java` validates `paper-api.alpaca.markets/v2`.
3. [x] **Clean Reset**: `CleanResetService` clears all legacy bot/position/order state.
4. [x] **Single-Instance Lease**: `LeaseManager` acquires PostgreSQL distributed row lock.
5. [x] **Three-Way Reconciliation**: `ReconciliationService` verifies initial 0 positions / 0 orders.
6. [x] **AI Rate & Cost Limiter**: Hard ceilings of 20 req/min, 500 req/day, \$10.00 daily spend.
7. [x] **Watchdog & Heartbeat**: 10 internal components monitored every cycle.
