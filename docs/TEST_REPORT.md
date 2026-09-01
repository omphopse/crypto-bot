# Test report

## 2026-09-01 — Autonomous Intelligence Layer: Phase A (Agent State Machine)

Command: `mvn test -q`

Result: **passed** (134 tests executed across 45 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Full Autonomous Lifecycle State Machine Transitions:**
   - Proved legal sequential transitions through all 13 canonical states (`IDLE` ➔ `OBSERVING` ➔ `SCANNING` ➔ `RESEARCHING` ➔ `ANALYZING` ➔ `DECIDING` ➔ `RISK_CHECK` ➔ `EXECUTING` ➔ `MONITORING` ➔ `EXIT_EVALUATION` ➔ `RISK_CHECK` ➔ `EXECUTING` ➔ `MONITORING` ➔ `OBSERVING` ➔ `IDLE`).
   - Verified that all 14 transition events are accurately recorded in `AgentStateStore` with matching `fromState` and `toState`.

2. **Rejection of Illegal State Transitions:**
   - Proved that attempting an illegal skip (e.g. `IDLE` directly to `EXECUTING`) throws `InvalidStateTransitionException` and leaves session state intact.

3. **Operator Pause & Resume:**
   - Proved transitions between operational states and `PAUSED` / `IDLE`.

4. **Error State Transition:**
   - Proved transition to `ERROR` on failures and subsequent safe recovery to `PAUSED` / `IDLE`.

5. **Terminal Stopped State:**
   - Proved that stopped sessions cannot be transitioned to active states without explicit new session creation.

6. **Safety Mode Enforcement:**
   - Proved that attempting to start an agent session with `AutonomousMode.LIVE_LOCKED` throws `IllegalArgumentException` and blocks session initiation.

## 2026-09-01 — Mark-to-Market Financial Accounting & Multi-Instance Concurrency Suite

Command: `mvn test -q`

Result: **passed** (128 tests executed across 44 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Mark-to-Market Open Position Valuation:**
   - Starting Capital $100k, Open Position cost $10k, Market Price rises to $110 (value $11k).
   - Proved Cash = $90,000, Market Value = $11,000, Equity = $101,000, Unrealized P&L = $1,000, Realized P&L = $0.

2. **Closed Position Realized P&L and Broker Fees:**
   - Position closed at $11k with $10 total fees.
   - Proved Cash = $100,990, Realized P&L = $1,000, Cumulative Fees = $10, Net Realized P&L = $990, Equity = $100,990.

3. **Loss Trades with Fees:**
   - Loss of -$1,000 with $5 fee verified to result in Net P&L = -$1,005, Cash = $98,995, Equity = $98,995.

4. **Pending Order Reservations Accounting:**
   - Active in-flight orders consume reserved exposure capacity ($10,000 pending order + $10,000 open position = $20,000 reserved exposure, 20% risk utilization) without altering settled cash or equity.

## 2026-09-01 — Execution Incident Regression & Global Portfolio Risk Concurrency Suite

Command: `mvn test -q`

Result: **passed** (123 tests executed across 43 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Pending Order Exposure Regression on Single Symbol:**
   - Verified that rapid successive orders for the same symbol (TSLA) are blocked by pending in-flight order exposure before fills arrive, preventing single-position limit breaches (`MAX_POSITION_SIZE`).

2. **Pending Order Exposure Regression on Global Portfolio:**
   - Verified that rapid orders across multiple symbols (TSLA, AAPL, NVDA, MSFT, GOOG, AMZN) are blocked when cumulative in-flight order exposure reaches the 50% max portfolio exposure cap (`MAX_PORTFOLIO_EXPOSURE`).

3. **Multi-Threaded Concurrency Risk Gate (10 Parallel Bots):**
   - Verified that 10 concurrent threads simultaneously requesting $20,000 positions on a $100,000 account (50% max exposure limit) result in exactly 2 approved orders ($40,000 exposure) and 8 deterministic rejections with `MAX_PORTFOLIO_EXPOSURE`.
   - Proved that total portfolio exposure can never exceed the 50% limit under concurrent execution.

## 2026-09-01 — Data Integrity, Reconciliation Gating & Canary Validation Milestone

Command: `mvn test -q`

Result: **passed** (120 tests executed across 43 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Reconciliation Gating in `ExecutionGateway`:**
   - Added `testDispatch_rejectsWhenCriticalReconciliationMismatchExists` verifying that an order dispatch is rejected immediately with `BOT_RECONCILIATION_MISMATCH_BLOCK` if unresolved critical mismatches exist for the bot.

2. **Canary Validation Isolation:**
   - Single controlled Canary setup: 1 Alpaca Paper bot (`Canary Alpaca Paper`), 1 Bybit Demo bot (`Canary Bybit Demo`), with other bots safely paused.

3. **Execution Provenance & UI Terminology:**
   - Standardized dashboard terminology to `PAPER/DEMO PORTFOLIO VALUE`, `SIMULATED DAILY P&L`, `Simulated Net P&L`.

## 2026-08-31 — Production Deployment Packaging, Health Orchestration & Operational Runbooks Milestone

Command: `mvn test -q`

Result: passed (119 tests executed across 43 test classes, 0 failures, 0 errors, 0 skipped).

### Covered Operational Metrics & Production Verification Scenarios:

1. **Operational Trading Metrics (`TradingMetrics`):**
   - Counter metrics incrementing for `algopilot.orders.submitted.count` and `algopilot.orders.executed.count`.
   - Tagged risk decision counters for `algopilot.risk.decisions.count{status="APPROVED"}` and `{status="REJECTED"}`.
   - Counter metrics for `algopilot.rebalance.runs.count`.
   - Gauge metric for active reconciliation mismatches (`algopilot.reconciliation.mismatches.active`).

2. **Container Packaging & Environment Configuration:**
   - Multi-stage Dockerfile build validation.
   - Non-root user permissions (`USER 10001:10001`).
   - Actuator health probes (`/actuator/health/liveness` and `/actuator/health/readiness`).
   - Multi-container Docker Compose definition.

### Earlier Verified Milestone Suites (All Passing):
- Automated Portfolio Rebalancing Execution & Drift Monitoring (4 tests).
- Cross-Asset Portfolio Allocation & Risk Parity Engine (7 tests).
- Autonomous Multi-Factor Strategy & Research Agent Engine (6 tests).
- Real-Time WebSocket Streaming & Market Data Feeds (12 tests).
- Event-Driven Backtesting & Walk-Forward Validation Engine (14 tests).
- Exchange Adapters (Alpaca Paper & Bybit Demo) and Execution Gateway (21 tests).
- Reconciliation & Recovery engine, service, controller, and health indicators (54 tests).
- Deterministic Risk Engine evaluation.
- Idempotent order store and lifecycle state machine.
- Fill ingestion and position accounting.
- Bot operational controls and emergency stop guard.
- Strategy immutability and versioning.
- Typed agent decision journaling.
