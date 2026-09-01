# Test report

## 2026-09-02 — Autonomous Intelligence Layer: Phase K (Strategy Research, Validation & Economic Edge Engine)

Command: `mvn test -q`

Result: **passed** (208 tests executed across 71 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Realistic Cost Modeling & Expectancy (`RealisticCostBacktestEngineTest`):**
   - Verified that simulations incorporate maker/taker fees, bid/ask spread, and dynamic slippage.
   - Verified calculation of Gross Expectancy, Net Expectancy, True Economic Net Result (deducting estimated AI and infrastructure overheads), and robustness warnings.

2. **Walk-Forward In-Sample vs Out-of-Sample Validation (`WalkForwardServiceTest`):**
   - Verified rolling window generation, out-of-sample quarantine, and out-of-sample degradation ratios.

3. **Parameter Sensitivity Sweeps (`ParameterSensitivityServiceTest`):**
   - Verified parameter grid testing and robustness classification (`ROBUST`, `FRAGILE`, `OVERFIT`).

4. **Strategy Health & Performance Drift (`StrategyHealthServiceTest`):**
   - Verified drift ratio calculations comparing backtest expectancy with live paper/demo execution and automated tagging of `STRATEGY_DEGRADATION`.

## 2026-09-02 — Autonomous Intelligence Layer: Phase J (Full Autonomous Canary Validation & End-to-End Operational Hardening)

Command: `mvn test -q`

Result: **passed** (204 tests executed across 67 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Continuous Autonomous Scheduling (`AutonomousBotRunnerTest`):**
   - Verified that `AutonomousBotRunner` acquires bot runtime leases, records heartbeats, executes position monitoring, and invokes autonomous cycle orchestration without manual intervention.
   - Verified that overlapping cycles on the same bot are prevented.
   - Verified that when a lease is held by another instance, the runner skips the bot safely.

2. **Multi-Bot Concurrent Account Safety (`MultiBotAccountSafetyStressTest`):**
   - Verified that 5 concurrent bots (BTC, ETH, NVDA, AAPL, TSLA) simultaneously requesting capital honor single-symbol and global portfolio exposure limits via the unified `RiskEngine`.

3. **Chaos Fault Injection & Fail-Safe Recovery (`ChaosFaultInjectionTest`):**
   - Verified that stale market data prevents new order generation (`MARKET_DATA_STALE`).
   - Verified that broker dispatch errors fail safely without duplicate order creation (`FAILED_BROKER`).

4. **End-to-End Forensic Traceability (`EndToEndForensicTraceabilityTest`):**
   - Proved 100% causal linkage from `TradingContext` ➔ `StructuredTradeDecision` ➔ `ValidatedTradeIntent` ➔ `RiskDecision` ➔ `OrderRecord` ➔ `OrderSubmissionResult` ➔ `ReconciliationService` ➔ `AuditEventWriter`.

## 2026-09-01 — Autonomous Intelligence Layer: Phase I (AI Cost Controls, Token Attribution & Rate Gating)

Command: `mvn test -q`

Result: **passed** (200 tests executed across 63 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Budget Threshold Evaluation (`AiCostGovernanceServiceTest`):**
   - Verified that when daily spending is $< 70\%$ of budget, status evaluates to `NORMAL`.
   - Verified that when daily spending crosses $70\%$, status transitions to `THROTTLED`.
   - Verified that when daily spending reaches $\ge 100\%$, status transitions to `BLOCKED` (`AI_BUDGET_EXCEEDED`).

2. **Token Attribution & Cost Calculation (`AiCostGovernanceServiceTest`):**
   - Verified that decision token counts are attributed across 7 prompt sections (`systemInstructions`, `marketContext`, `strategyContext`, `portfolioContext`, `riskContext`, `researchEvidence`, `outputTokens`).
   - Verified that costs are calculated against active `AiModelPricing` per million tokens.
   - Verified that cost records are saved to `ai_cost_events` and recorded in the audit trail.

## 2026-09-01 — Autonomous Intelligence Layer: Phase H (Heartbeat Watchdog, Health Probes & Automated Fault Recovery)

Command: `mvn test -q`

Result: **passed** (196 tests executed across 62 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Watchdog Surveillance (`WatchdogServiceTest`):**
   - Verified that when bot heartbeat is stale ($> 30\text{s}$), Watchdog transitions bot to `PAUSED` and records critical health event (`BOT_STALE`).
   - Verified that when runtime lease expires, Watchdog transitions bot to `PAUSED` (`RUNTIME_LEASE_LOST`).
   - Verified that when order is stuck in `SUBMITTED` ($> 30\text{s}$), Watchdog transitions bot to `PAUSED` and triggers reconciliation (`ORDER_STUCK`).

2. **Distributed Runtime Leases (`LeaseManagerTest`):**
   - Single instance successfully acquires bot lease.
   - Second instance attempting to acquire active lease for same bot is rejected.
   - Expired lease allows takeover by new instance.

3. **Automated Recovery & Restart Safety (`RecoveryServiceTest`):**
   - Clean broker reconciliation results in `COMPLETED` recovery run.
   - Discrepant broker reconciliation results in `FAILED` recovery run and keeps bot safely paused.
   - Application startup recovery sweeps previously running bots and sets them to `PAUSED`/safe state without auto-trading.

## 2026-09-01 — Autonomous Intelligence Layer: Phase G (Autonomous Position Monitoring & Dynamic Exits)

Command: `mvn test -q`

Result: **passed** (189 tests executed across 59 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Stop Loss & Trailing Stop Managers (`StopLossManagerTest`):**
   - Valid stop move (raising stop for long position) validated.
   - Risky stop move (lowering stop for long position) rejected.
   - Setting stop above market price rejected.
   - Long & short stop trigger boundary detection.

2. **Deterministic Exit Precedence (`ExitConditionEvaluatorTest`):**
   - Nominal market price returns `HOLD` (no exit).
   - Hard stop loss trigger detection.
   - Take profit trigger detection.
   - Trailing stop ratchet trigger detection.

3. **Position Monitor & Safety Override (`PositionMonitorServiceTest`, `CriticalEndToEndPositionMonitoringTest`):**
   - Verified that when hard stop loss is triggered, exit order executes through `RiskEngine` ➔ `ExecutionGateway` ➔ `ReconciliationService` and records `POSITION_EXIT_EXECUTED`.
   - In `OBSERVE_ONLY` mode, exit is recorded as `POSITION_EXIT_OBSERVE_ONLY` and zero orders are dispatched.
   - **Safety Invariant**: When AI proposes `HOLD`, but hard stop loss is breached, hard stop **unconditionally overrides AI and closes position**.
   - **Critical End-to-End Test**: Open Position ➔ Market Moves Down ➔ Monitor Cycle ➔ Hard Stop Loss Triggered ➔ Exit Order Evaluated by `RiskEngine` ➔ Order Dispatched via `ExecutionGateway` ➔ Position Closed ➔ `ReconciliationService` Post-Execution Check ➔ Forensic Audit Logging.

## 2026-09-01 — Autonomous Intelligence Layer: Phase F (Strategy Validation, Risk Alignment & Autonomous Paper/Demo Execution)

Command: `mvn test -q`

Result: **passed** (181 tests executed across 56 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Deterministic Strategy Validation (`StrategyValidationServiceTest`):**
   - Valid trade intent correctly verified and passed against deployed strategy definition.
   - Expired trade intent rejected (`DECISION_EXPIRED`).
   - Strategy version mismatch rejected (`STRATEGY_VERSION_MISMATCH`).
   - Market price deviation $> 0.25\%$ rejected (`DECISION_PRICE_DEVIATION_EXCEEDED`).
   - Position exit / reduction intent without active position rejected (`POSITION_NOT_FOUND_FOR_EXIT`).

2. **Autonomous End-to-End Pipeline & Observe-Only Gating (`AutonomousExecutionPipelineE2ETest`):**
   - **Full End-to-End Simulation**: Market observation ➔ Scanner candidate ➔ Research evidence ➔ `TradingContext` ➔ Fake LLM reasoner ➔ Decision validation ➔ Strategy validation ➔ `RiskEngine` evaluation ➔ `ExecutionGateway` order dispatch ➔ Paper order acknowledgment ➔ `ReconciliationService` post-execution check ➔ Audit trail recording.
   - **Observe-Only Mode**: In `OBSERVE_ONLY` mode, validated that `OBSERVE_ONLY_RECORDED` status is generated and zero orders are created or dispatched to brokers.

## 2026-09-01 — Autonomous Intelligence Layer: Phase E (Structured LLM Decision Engine)

Command: `mvn test -q`

Result: **passed** (174 tests executed across 54 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Schema & Semantic Decision Validation (`StructuredDecisionValidatorTest`):**
   - Valid BUY decision correctly validated with stop-loss/take-profit boundaries.
   - Unsupported symbol proposal rejected (`UNSUPPORTED_SYMBOL`).
   - Non-positive quantity or price rejected (`INVALID_NON_POSITIVE_QUANTITY`).
   - Logically inverted stop-loss (BUY stop loss above entry price) rejected (`INVALID_BUY_STOP_LOSS_ABOVE_PRICE`).
   - Synthetic/hallucinated research evidence reference rejected (`UNSUPPORTED_SYNTHETIC_EVIDENCE`).
   - Context hash mismatch rejected (`CONTEXT_HASH_MISMATCH`).

2. **Decision Engine & Budget Enforcement (`LLMDecisionEngineServiceTest`):**
   - Verified that `analyzeBot` generates validated `StructuredTradeDecision` records and persists them to the store.
   - Verified that exhausted AI rate limit or token budget gracefully returns a failed decision (`AI_RATE_OR_BUDGET_EXCEEDED`) without calling the provider.
   - Verified via reflection that `LLMDecisionEngineService` possesses **zero order/dispatch execution authority**.

## 2026-09-01 — Autonomous Intelligence Layer: Phase D (Typed, Prompt-Safe Context Builder)

Command: `mvn test -q`

Result: **passed** (166 tests executed across 52 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Complete Context Assembly (`ContextBuilderServiceTest`):**
   - Verified that `ContextBuilderService` builds all 12 sub-contexts and a top-level `TradingContext`.
   - Verified that SHA-256 `contextHash` fingerprint is deterministically computed.
   - Verified that `ResearchEvidenceContext` carries `isUntrustedExternalData = true`.
   - Verified that `CONTEXT_BUILD_COMPLETED` audit event is published.

2. **Stale Market Data Handling (`ContextBuilderServiceTest`):**
   - Verified that observations older than 5 minutes trigger `FreshnessStatus.STALE`.
   - Verified that stale market data sets `safety.marketDataFresh = false` and `safety.executionAllowed = false` with block reason `MARKET_DATA_STALE`.

3. **Reconciliation Discrepancy Gating (`ContextBuilderServiceTest`):**
   - Verified that active critical reconciliation mismatches flag `reconciliation.isTradingBlocked = true`.
   - Verified that `safety.reconciliationHealthy = false` and `safety.executionAllowed = false` with block reason `RECONCILIATION_MISMATCH_PRESENT`.

4. **Zero Execution Authority Invariant (`ContextBuilderServiceTest`):**
   - Verified via reflection that `ContextBuilderService` contains **zero trading/order dispatch methods** and cannot place orders.

## 2026-09-01 — Autonomous Intelligence Layer: Phase C (Research Service & Secure Browser Abstraction)

Command: `mvn test -q`

Result: **passed** (161 tests executed across 51 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **SSRF & Domain Security Validation (`DomainSecurityValidatorTest`):**
   - Valid HTTPS URLs accepted (`https://www.sec.gov`).
   - Insecure non-HTTPS schemes rejected (`http://`, `file://`, `javascript:`, `data:`).
   - Loopback and local hosts rejected (`localhost`, `127.0.0.1`, `foo.localhost`).
   - Cloud metadata endpoints rejected (`metadata.google.internal`, `instance-data`, `169.254.169.254`).
   - RFC 1918 private subnets identified and rejected (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
   - Non-standard ports rejected for SSRF protection (`https://example.com:8080`).

2. **Multi-Vector Prompt Injection Defense (`PromptInjectionDetectorTest`):**
   - Clean financial articles classified `CLEAN`.
   - Single instruction override attempts classified `SUSPICIOUS`.
   - Multi-vector instruction and credential exfiltration attacks classified `BLOCKED`.
   - Embedded `<script>` tags detected and isolated.

3. **HTML Sanitization & Document Normalization (`ContentSanitizerTest`):**
   - Script and style blocks completely removed.
   - HTML tags stripped and HTML entities decoded.
   - Whitespace collapsed and max character length enforced.

4. **Structured Evidence & Execution Isolation (`ResearchServiceTest`):**
   - Ingests raw pages, computes SHA-256 hashes, extracts structured `ResearchEvidence`, and records audit trails.
   - Prompt injection attempts trigger `RESEARCH_PROMPT_INJECTION_DETECTED` audit logs and quarantine.
   - Rate limiting strictly enforced (`RATE_LIMITED`).
   - Verified that `ResearchService` contains **zero trading methods** and cannot create orders.

## 2026-09-01 — Autonomous Intelligence Layer: Phase B (Market Observation & Scanner)

Command: `mvn test -q`

Result: **passed** (145 tests executed across 47 test classes, 0 failures, 0 errors, 0 skipped, 0 network dependencies).

### Covered Verification Scenarios:

1. **Market Observation Ingestion & Validation:**
   - Valid observations with price, volume, and quotes accepted and audited (`MARKET_OBSERVATION_RECEIVED`).
   - Invalid non-positive price ($price \le 0$) rejected and audited (`MARKET_OBSERVATION_REJECTED`).
   - Crossed market quotes ($bid > ask$) rejected with `CROSSED_MARKET`.
   - Inverted OHLC bars ($high < open$ or $low > close$) rejected with `INVALID_HIGH`.
   - Stale market observations ($freshnessMs > 60,000ms$) marked `STALE` and audited (`MARKET_DATA_STALE`).

2. **Deterministic Market Scanner & Technical Indicators:**
   - Enforced warm-up period ($\ge 20$ historical bars); short history yields `isWarmedUp=false` and `NO_CANDIDATE`.
   - Stale observation inputs abort scanning with `SKIPPED_STALE_DATA`.
   - Bullish momentum detection ($EMA9 > EMA21$ and $RSI > 50$ and $Price \ge EMA9$) outputs `MOMENTUM` candidate.
   - Oversold condition ($RSI < 30$) outputs `OVERSOLD` candidate.
   - 20-bar high breakout with volume expansion outputs `BREAKOUT` candidate.
   - Volume spikes ($> 2.5\times$ average) output `VOLUME_ANOMALY` candidate.
   - Quiet markets output `NO_CANDIDATE`.
   - Verified that `MarketScanner` has zero trading dependencies and cannot place orders.

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
