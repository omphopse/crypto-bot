# ALGOPILOT — DATA INTEGRITY & AUDIT REPORT

**Date:** 2026-09-01  
**Scope:** Data Sources, Provider Integrations, Seed/Mock Inventories, and Simulation Boundaries  

---

## 1. Executive Summary
This report provides an audit of all financial, order, position, price, and intelligence data within the Algopilot platform. It categorizes every data origin into:
1. **Real Provider Data** (Alpaca Paper API, Bybit Demo API)
2. **Test Data** (Deterministic fixtures in `/src/test`)
3. **Mock Data** (Mock HTTP clients used in test execution)
4. **Seeded Data** (Initial development entities seeded by `DataSeeder.java`)
5. **Hardcoded / Static Values** (Template placeholders in UI html files)
6. **Simulated Execution Paths** (Fallback paper sandbox simulations when dummy credentials are provided)

---

## 2. Data Origin Matrix

| Domain | Attribute | Origin Type | Source / Component | Live / Network Status |
| :--- | :--- | :--- | :--- | :--- |
| **Account Balances** | Cash, Buying Power, Equity | **Real Provider** (Alpaca Paper) | `AlpacaPaperAdapter.fetchBalance` (`GET /v2/account`) | Paper Endpoint (`https://paper-api.alpaca.markets/v2`) |
| **Account Balances** | USDT Wallet Balance, Total Equity | **Real Provider** (Bybit Demo) | `BybitDemoAdapter.fetchBalance` (`GET /v5/account/wallet-balance`) | Demo Endpoint (`https://api-demo.bybit.com`) |
| **Market Data** | Quotes, Ticks, OHLCV Bars | **Real Provider / Feed** | `AlpacaPaperMarketFeed`, `BybitDemoMarketFeed` | Paper / Demo Market Stream |
| **Market Data** | Synthetic Backtest Candles | **Synthetic Generator** | `BacktestController.generateSyntheticCandles` | In-memory deterministic generator |
| **Order Dispatch** | Order Submission & Acknowledgment | **Real Provider** (Alpaca Paper) | `AlpacaPaperAdapter.submitOrder` (`POST /v2/orders`) | Paper Endpoint |
| **Order Dispatch** | Order Submission & Acknowledgment | **Real Provider** (Bybit Demo) | `BybitDemoAdapter.submitOrder` (`POST /v5/order/create`) | Demo Endpoint |
| **Order Cancellation**| Order Cancel Requests | **Real Provider** | `AlpacaPaperAdapter.cancelOrder`, `BybitDemoAdapter.cancelOrder` | Paper / Demo Endpoint |
| **Trade Fills** | Execution Activity | **Real Provider** | `AlpacaPaperAdapter.fetchFills`, `BybitDemoAdapter.fetchFills` | Paper / Demo Endpoint |
| **Positions** | Live Position Balances | **Real Provider / Local Store** | `PositionStore`, `AlpacaPaperAdapter.fetchPositions`, `BybitDemoAdapter.fetchPositions` | Paper / Demo Endpoint + Postgres |
| **Reconciliation** | Mismatch Engine & Runs | **Deterministic Engine** | `ReconciliationEngine`, `ReconciliationService` | Side-effect-free comparison |
| **Canary Bots** | Bot Definitions & Statuses | **Database / Seed** | `DataSeeder.java` ➔ `JdbcBotStore` | Seeded in Postgres |
| **Strategies** | Strategy Definitions & Versions | **Database / Seed** | `DataSeeder.java` ➔ `JdbcStrategyStore` | Seeded in Postgres |
| **Agent Reasoning** | Alpha Theses & Decision Journal | **Database / Ledger** | `DecisionJournalService` ➔ `JdbcAgentDecisionStore` | Immutable Postgres Sink |
| **Audit Events** | System Action Logs | **Database / Audit Sink** | `AuditEventWriter` ➔ `audit_events` | Immutable Append-Only Ledger |

---

## 3. Seeded & Mock Data Inventory

### 3.1 `DataSeeder.java`
* **Strategies Seeded**:
  - `Crypto Momentum` (v1)
  - `US Equity Trend` (v1)
  - `ETH Reversion` (v1)
* **Canary Bots Configuration**:
  - `Canary Alpaca Paper` (`Crypto Momentum`, `ALPACA_PAPER`, `PAPER`) ➔ **`RUNNING`**
  - `Canary Bybit Demo` (`ETH Reversion`, `BYBIT_DEMO`, `DEMO`) ➔ **`RUNNING`**
  - `US Equity Trend` (`US Equity Trend`, `ALPACA_PAPER`, `PAPER`) ➔ **`PAUSED`** (Controlled canary isolation)
* **Initial Synthetic Positions**:
  - Seeded initial marked-to-market positions for baseline visualization (`BTC/USD`, `NVDA`, `ETH/USD`).
* **Initial Decision Journal Entries**:
  - 3 initial alpha reasoning entries to populate the immutable audit timeline.

### 3.2 Offline Sandbox Simulation Fallback
* When placeholder credentials (e.g. `paper_dummy_key_id` or `demo_dummy_key_id`) are configured or during offline sandbox execution, `AlpacaPaperAdapter` and `BybitDemoAdapter` gracefully provide simulated paper execution acknowledgments rather than crashing.
* All production adapters enforce strict non-negotiable compile-time and runtime live trading lockout (`LIVE_TRADING_DISABLED`).

---

## 4. Test Data & Isolation Policy
* **Zero External Network Dependencies**: All unit and integration tests (`*Test.java`) execute against mocked HTTP clients, in-memory databases, or local test fixtures.
* **Deterministic Fixtures**: Backtests, factor synthesis, and walk-forward engines use fixed candle arrays and deterministic math seeds.
* **Safety Tests**: `EmergencyStopInvariantTest` and `LiveTradingBoundaryTest` ensure live trading is blocked across all code paths.

---

## 5. UI Terminology Standardization
To prevent any misinterpretation of simulated performance as real monetary profits:
* **"Portfolio Equity"** is labeled as **"Paper/Demo Portfolio Equity"**.
* **"PORTFOLIO VALUE"** is labeled as **"PAPER/DEMO PORTFOLIO VALUE"**.
* **"DAILY P&L"** is labeled as **"SIMULATED DAILY P&L"**.
* **"All-Time Net Profit Made"** is labeled as **"Simulated Net P&L"**.
* Every trade item includes execution provenance metadata linking provider, environment, provider order ID, client order ID, and execution timestamps.
