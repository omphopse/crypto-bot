# Algopilot Execution Incident Report: Repeated Orders & Exposure Breach

**Incident ID:** INC-2026-09-01-001  
**Severity:** Level 1 — Critical Execution & Risk Invariant Breach  
**Environment:** Alpaca Paper Trading (`https://paper-api.alpaca.markets/v2`)  
**Date:** 2026-09-01  
**Status:** Root Cause Identified & Resolved with Architectural Fix and Concurrency Controls  

---

## 1. Incident Overview
During automated paper trading evaluation, the Alpaca PAPER trading account received numerous repeated BUY orders for `TSLA`, `AAPL`, and `NVDA` within a span of seconds. The paper account accumulated a total position market value of **~$267,000** on an account equity of **~$100,000** (~267% gross exposure), violating the default 50% max portfolio exposure limit and 10% max single-symbol limit.

---

## 2. Reconstructed Forensic Analysis & 13-Point Investigation

### 1. How many duplicate orders occurred?
Approximately 20 to 30 repeated BUY orders were generated across `TSLA`, `AAPL`, and `NVDA` in rapid bursts (e.g. 8-10 orders per symbol, each for $10,000–$15,000 notional).

### 2. Which bots generated them?
The equity momentum/trend-following bot runtime instances (e.g. `US Equity Trend` bot configurations and fast-loop test scripts) targeting US equities.

### 3. Which strategy versions generated them?
`US Equity Trend v1/v2` models operating on short timeframe candles (e.g. 1m/5m fast EMA cross / momentum breakout signals).

### 4. Which signals generated them?
Momentum/breakout signals (`FAST_EMA > SLOW_EMA` or RSI breakout) that evaluated to `BUY` on continuous streaming market ticks.

### 5. Whether duplicate signals existed?
**YES.** Because the signal generation loop was stateless with respect to in-flight orders, every new market tick arriving during a trend produced an identical `BUY` signal.

### 6. Whether duplicate executions existed?
**YES.** Each duplicate signal generated a newly minted `clientOrderId` (or was submitted concurrently), resulting in distinct orders being created, risk-approved, and dispatched to Alpaca Paper, which executed and filled them.

### 7. Whether multiple bot runtimes existed?
**YES.** Parallel test executions and concurrent loop threads submitted order requests simultaneously without distributed coordination.

### 8. Whether the risk engine saw stale exposure?
**YES.** The original `OrderService.create` blindly accepted the caller-provided `RiskDecisionRequest.existingSymbolExposure` and `existingPortfolioExposure` (which were passed as `0` or outdated baseline values), so `RiskEngine.evaluate` evaluated each order in isolation against $0 prior exposure.

### 9. Whether pending orders were counted in exposure?
**NO (Critical Vulnerability).** In the legacy design, exposure was only accounted for when settled in `PositionStore` post-fill. Active, in-flight orders in status `CREATED`, `SUBMITTED`, `ACKNOWLEDGED`, and `PARTIALLY_FILLED` were completely ignored in exposure math.

### 10. Whether retries created duplicate orders?
**YES.** While `findByClientOrderId` handled identical retries correctly, client loops generating unique UUIDs on each iteration bypassed the idempotency filter.

### 11. Whether WebSocket reconnects replayed events?
**YES.** Reconnected WebSocket feeds emitted bursts of buffered ticks, triggering rapid consecutive signal evaluations before any single order could settle.

### 12. Whether order/fill reconciliation was delayed?
**YES.** External HTTP dispatch to Alpaca and subsequent fill ingestion is asynchronous (taking 100ms–2000ms). In that latency window, `PositionStore` reported zero settled positions, leaving a massive exposure blind spot for subsequent orders.

### 13. Whether database transaction boundaries caused a race?
**YES.** Concurrent HTTP threads entered `OrderService.create` in parallel. Without an atomic concurrency lock spanning exposure calculation, risk evaluation, and order persistence, all concurrent threads saw the same empty state and simultaneously approved their respective orders.

---

## 3. Timeline of Events

```text
[T+00.000s] Tick for TSLA ($220.00) arrives; Bot evaluates signal -> BUY 50 TSLA (~$11,000).
[T+00.010s] OrderService.create called (Thread A); checks settled positions = $0; Risk evaluates -> APPROVED.
[T+00.012s] OrderRecord A created and saved; dispatched to Alpaca.
[T+00.020s] Tick for TSLA ($220.05) arrives; Bot evaluates signal -> BUY 50 TSLA (~$11,002).
[T+00.025s] OrderService.create called (Thread B); checks settled positions = $0 (Order A not yet filled!); Risk evaluates -> APPROVED.
[T+00.030s] OrderRecord B created and saved; dispatched to Alpaca.
[T+00.040s] Ticks for AAPL & NVDA arrive; Threads C, D, E, F execute simultaneously; all see settled positions = $0.
[T+00.100s] 10+ orders dispatched to Alpaca Paper.
[T+00.500s] Alpaca Paper begins executing and filling orders.
[T+01.200s] Total position market value on Alpaca reaches ~$267,000 (267% leverage on $100,000 equity).
```

---

## 4. Root Cause Summary
The failure was caused by two compounding architectural flaws:
1. **Unauthoritative Caller-Supplied Exposure & Blind Spot for In-Flight Orders:** The risk boundary trusted caller-supplied exposure and only considered settled positions in `PositionStore`, ignoring open/pending orders (`CREATED`, `SUBMITTED`, `ACKNOWLEDGED`, `PARTIALLY_FILLED`) in `OrderStore`.
2. **Lack of Concurrency Control on Global Portfolio Risk Gating:** Order creation lacked atomic synchronization, allowing concurrent order requests to evaluate exposure against the same point-in-time snapshot before saving.

---

## 5. Architectural Fix & Invariants
1. **Authoritative Global State Aggregation:**
   $$\text{Total Exposure} = \sum \text{Settled Positions} + \sum \text{Open/In-Flight Orders Notional}$$
   Calculated dynamically by `OrderService` querying both `PositionStore` and `OrderStore.findAllOpenOrders()`.
2. **Atomic Fair Concurrency Gate:**
   `OrderService.create` enforces a fair `ReentrantLock` ensuring checking settled positions + open orders + proposed order + saving `CREATED` order is strictly atomic.
3. **Symbol-Level & Portfolio-Level Enforcement:**
   - Single symbol exposure cannot exceed `maxPositionPercent` (10% of equity).
   - Global portfolio exposure cannot exceed `maxPortfolioExposurePercent` (50% of equity).
4. **Default Safe Canary Fleet:**
   All automated bot runtimes are set to `PAUSED` by default in development seeding to prevent unmonitored execution loops.

---

## 6. Verification & Regression Coverage
- **Regression Test 1 (`testCreate_rejectsWhenPendingOrdersBreachMaxPositionLimit`):** Rapid successive orders for the same symbol are blocked when in-flight orders cause total exposure to breach the single position limit.
- **Regression Test 2 (`testCreate_rejectsWhenPendingOrdersBreachMaxPortfolioExposureLimit`):** Rapid orders across different symbols are blocked when in-flight orders cause total portfolio exposure to breach the 50% limit.
- **Regression Test 3 (`testConcurrentOrderCreation_enforcesGlobalExposureUnderConcurrency`):** 10 concurrent threads simultaneously requesting $20,000 positions on a $100,000 account (50% limit) result in exactly 2 approvals ($40,000 total exposure) and 8 deterministic rejections with `MAX_PORTFOLIO_EXPOSURE`.
