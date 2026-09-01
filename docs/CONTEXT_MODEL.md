# Typed, Prompt-Safe Context Model

## 1. Core Principle
The `TradingContext` represents the deterministic, strongly-typed data assembly layer for future autonomous decision evaluations.
- **Zero Execution Capability**: The Context Builder possesses zero trading methods, zero order dispatch logic, and cannot execute trades.
- **Authoritative Subsystems**: Context assembly aggregates data directly from authoritative stores (`PortfolioAccountingService`, `MarketDataStore`, `MarketScanStore`, `StrategyStore`, `ReconciliationStore`, `ResearchStore`, `OrderStore`).

## 2. Context Schema & Sub-Contexts

| Sub-Context | Domain Description | Trust Classification |
| :--- | :--- | :--- |
| `MarketContext` | Real-time quote/candle observations, spread, volume, freshness | Provider-verified data |
| `IndicatorContext` | Deterministic technical indicators (EMA, SMA, RSI, MACD, ATR, Bollinger Bands) | Deterministic math |
| `ScannerContext` | Quantitative trigger patterns & anomaly scores | Deterministic scanner output |
| `StrategyContext` | Immutable strategy version, parameters, and timeframes | System authoritative |
| `PortfolioContext` | Mark-to-market equity, cash, cost basis, unrealized/realized P&L, reserved exposure | Authoritative accounting |
| `PositionContext` | Marked open positions with average entry prices and unrealized P&L | Settled inventory |
| `OpenOrderContext` | In-flight active orders and reserved exposure | Committed reservations |
| `RiskContext` | Risk state, drawdowns, single-symbol exposure, emergency stop status | Authoritative risk gating |
| `ReconciliationContext`| Reconciliation status, mismatch count, trading block flag | Reconciliation monitor |
| `ResearchEvidenceContext`| Sanitized web research snippets, topics, sources, relevance scores | **UNTRUSTED EXTERNAL DATA** |
| `PerformanceContext` | Historical performance metrics (win rate, profit factor, net P&L) | Historical observation |
| `FreshnessSummary` | Component latency and overall freshness status (`FRESH`, `AGING`, `STALE`, `UNAVAILABLE`) | Freshness telemetry |
| `SafetySummary` | Gate evaluation (`marketDataValid`, `riskStateValid`, `reconciliationHealthy`, `executionAllowed`) | Safety monitoring |

## 3. Data Freshness Classification
- **FRESH**: Data age $\le 60,000\text{ ms}$.
- **AGING**: Data age $60,000\text{ ms} < t \le 300,000\text{ ms}$.
- **STALE**: Data age $> 300,000\text{ ms}$ (categorically disables `executionAllowed`).
- **UNAVAILABLE**: Missing observation data (never fabricated or assumed $0.0$).

## 4. Deterministic Hashing & Reproducibility
- Every `TradingContext` computes a SHA-256 `contextHash` over bot ID, session ID, symbol, price, equity, and 10-second time buckets.
- The hash ensures complete reproducibility and auditability of future autonomous decisions.
