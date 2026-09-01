# Autonomous Execution Pipeline (Paper & Demo)

## 1. Architectural Pipeline & Trust Invariant
The autonomous trading pipeline strictly connects the advisory LLM reasoner to the deterministic, non-bypassable execution engine.

```
Market Observation & Scanner
           ↓
Sandboxed Web Research (Untrusted Evidence)
           ↓
Typed TradingContext (SHA-256 Fingerprinted)
           ↓
LLM Reasoner (StructuredTradeDecision Hypothesis)
           ↓
Semantic Decision Validation (StructuredDecisionValidator)
           ↓
Deterministic Strategy Validation (StrategyValidationService)
           ↓
Authoritative Risk Engine (RiskDecisionService)
           ↓
Non-Bypassable Execution Gateway (ExecutionGateway)
           ↓
Alpaca Paper / Bybit Demo Adapter
           ↓
Automated Post-Execution Reconciliation (ReconciliationService)
           ↓
Forensic Audit Log
```

## 2. Modes of Autonomous Operation
- **`OBSERVE_ONLY`**:
  - Full pipeline runs (market scan, research, context assembly, LLM decision hypothesis, strategy validation, risk simulation).
  - Categorically blocks order creation and broker dispatch.
  - Records `OBSERVE_ONLY_RECORDED` in execution history and dashboard.
- **`PAPER_AUTONOMOUS`**:
  - Automatically dispatches approved orders to **Alpaca Paper** (`https://paper-api.alpaca.markets/v2`).
  - Idempotent execution with automated post-dispatch reconciliation.
- **`DEMO_AUTONOMOUS`**:
  - Automatically dispatches approved orders to **Bybit Demo** (`https://api-demo.bybit.com`).
  - Idempotent execution with automated post-dispatch reconciliation.
- **`LIVE_LOCKED` / `LIVE`**:
  - Strictly blocked by `LIVE_TRADING_DISABLED` invariant.

## 3. Multi-Layered Validation Invariants
1. **Strategy Version Invariant**: Every trade intent MUST match the exact immutable version deployed to the bot (`bot.strategyVersionId()`).
2. **Price Deviation Invariant**: Decision reference prices deviating $> 0.25\%$ from current verified market prices are rejected (`DECISION_PRICE_DEVIATION_EXCEEDED`).
3. **Position Invariant**: `CLOSE` and `REDUCE` actions require verified open inventory.
4. **Risk Gating Invariant**: All orders MUST receive deterministic approval from `RiskEngine` (enforcing balance, drawdowns, single-symbol limits, and portfolio limits).
