# Trading safety

ALGOPILOT must fail closed. The deterministic risk engine is the final order authority and evaluates balance, buying power, per-symbol and portfolio exposure, leverage, daily loss, drawdown, consecutive loss, trade frequency, price freshness, spread, slippage, duplicates, strategy state, bot state, and kill-switch state.

## Permitted Trading Modes
- Alpaca: `PAPER` only (`https://paper-api.alpaca.markets/v2`)
- Bybit: `DEMO` only (`https://api-demo.bybit.com`)
- `LIVE`: Strictly disabled and rejected at all configuration and execution boundaries.

## Global Portfolio Risk & Concurrency Invariants
1. **Authoritative State Synthesis**: `OrderService` evaluates exposure authoritatively from settled positions in `PositionStore` PLUS all open/in-flight orders (`CREATED`, `SUBMITTED`, `ACKNOWLEDGED`, `PARTIALLY_FILLED`) in `OrderStore`. Caller-supplied exposure numbers cannot bypass backend accounting.
2. **Atomic Concurrency Gate**: Order creation is guarded by an atomic fair concurrency lock (`ReentrantLock`) and database-backed row-level locking (`SELECT ... FOR UPDATE` on `portfolio_accounts`) preventing multi-process race conditions.
3. **Hard Portfolio Exposure Cap**: Global portfolio exposure cannot exceed `maxPortfolioExposurePercent` (default 50% of account equity).
4. **Hard Symbol Position Cap**: Single-symbol exposure cannot exceed `maxPositionPercent` (default 10% of account equity).
5. **Default Paused Fleet**: All automated bot runtimes in development configuration start in `PAUSED` status by default to prevent runaway signal execution.

## Financial Accounting & Mark-to-Market Invariants
1. **Mark-to-Market Invariant**: Portfolio Equity MUST strictly equal $\text{Cash} + \text{Current Market Value of Open Positions}$, identically matching $\text{Starting Capital} + \text{Realized P\&L} + \text{Unrealized P\&L} - \text{Cumulative Fees}$.
2. **Zero Double-Counting**: Realized P&L from closed positions, unrealized P&L from open inventory, and broker execution fees are strictly partitioned.
3. **Reservation Lifecycle**: In-flight orders consume buying power immediately upon risk gating approval; cancelled, rejected, or filled orders release/update reservations synchronously.

## Market Observation & Scanner Safety Invariants
1. **Zero Execution Capability**: `MarketScanner` evaluates technical candidate conditions and produces `ScanResult` records only. It contains zero order submission logic, zero broker execution clients, and cannot place orders.
2. **Untrusted Market Data Validation**: Observations with non-positive prices, negative volumes, inverted OHLC bars ($high < open$ or $low > close$), or crossed quotes ($bid > ask$) are automatically marked invalid and rejected.
3. **Deterministic Stale Data Gating**: Observations older than the configured freshness threshold ($60,000ms$) are marked `STALE` and categorically abort scanner candidate generation.
4. **Warm-up History Invariant**: Technical indicators require a minimum of 20 historical bars; scans with insufficient history output `NO_CANDIDATE` and are flagged as not warmed up.

## Autonomous Web Research & Evidence Invariants
1. **Information-Only Classification**: External web pages and search documents are strictly classified as untrusted data. They possess zero execution authority and cannot create orders or bypass risk limits.
2. **SSRF & Private Network Isolation**: HTTP requests to `localhost`, `127.0.0.1`, RFC 1918 private subnets, link-local IPs, cloud metadata endpoints (`169.254.169.254`), non-standard ports, and non-HTTPS schemes are rejected unconditionally.
3. **Prompt Injection Quarantine**: Content containing instruction-override triggers ("ignore previous instructions", "system prompt", "reveal credentials") is quarantined and tagged as `SUSPICIOUS` or `BLOCKED` to prevent malicious prompt manipulation.
4. **Zero Credential Exposure**: The research subsystem does not receive or store broker API keys, API secrets, or exchange credentials.

## Context Builder Safety Invariants
1. **Data Assembly Only**: `ContextBuilderService` aggregates system state and external research into typed, immutable `TradingContext` structures without modifying state or executing orders.
2. **Deterministic Safety Gating**: Evaluates safety gates (`marketDataValid`, `marketDataFresh`, `riskStateValid`, `reconciliationHealthy`, `strategyActive`, `executionAllowed`). Stale market data ($>300,000\text{ ms}$) or reconciliation critical mismatches categorically set `executionAllowed = false`.
3. **Zero Hallucination / No Fabrication**: Missing data is explicitly typed as `UNAVAILABLE` or `UNKNOWN`; zero values are never substituted for absent observations.
4. **Context Hashing & Provenance**: Every context generates a SHA-256 fingerprint over time buckets and financial marks for decision reproducibility.

## Structured LLM Decision Engine Invariants
1. **Hypothesis Generation Only**: `LLMDecisionEngineService` produces advisory `StructuredTradeDecision` records. It cannot place orders, cancel orders, or interact directly with broker execution interfaces.
2. **Schema & Semantic Validation**: `StructuredDecisionValidator` rejects decisions with mismatched symbols, unsupported actions, negative quantities, invalid stop-loss/take-profit prices, or mismatched context hashes.
3. **Evidence Provenance Verification**: All evidence references in decisions must match persisted research evidence; synthetic/hallucinated evidence IDs trigger immediate decision rejection.
4. **Rate & Budget Controls**: Decisions are capped by request rate limits (20 req/min, 500 req/day) and daily token cost budgets ($10.00 USD/day); limit exhaustion triggers `ValidationStatus.FAILED`.

## Production Incident Response & Safety Invariants
1. **Runbook Adherence**: All operator actions during reconciliation discrepancies, emergency stop events, and broker reconnects MUST follow procedures in `docs/RUNBOOK.md`.
2. **Health Probe Integrity**: Container liveness/readiness probes verify database connectivity and Actuator health status before routing traffic.
3. **Hardened Unprivileged Execution**: Production containers execute exclusively as an unprivileged non-root user (`USER 10001:10001`).

## Automated Portfolio Rebalancing Invariants
1. **Mandatory Risk Evaluation**: Every synthesized rebalance order intent must pass `RiskDecisionService.evaluate(...)` before order creation and execution.
2. **Deterministic Drift Threshold**: Rebalance executions trigger only when maximum asset weight drift exceeds the defined threshold ($\ge 5.0\%$) or upon explicit operator request.
3. **Execution State Integrity**: Rebalance runs transition through explicit status lifecycle states (`STARTED`, `COMPLETED`, `FAILED_RISK_GATING`).

## Cross-Asset Portfolio Allocation & Risk Parity Invariants
1. **Advisory Weighting**: Target weights calculated by `RiskParityAllocator` represent capital targets. Any resulting rebalancing orders must individually pass through `RiskEngine`.
2. **Strict Sum-to-One Normalization**: Target capital weights are normalized such that $\sum w_i = 1.0000$ using exact `BigDecimal` precision.
3. **VaR/CVaR Exposure Monitoring**: Parametric VaR (95%) and CVaR (95%) metrics monitor cross-asset tail risk to protect against simultaneous correlated market drawdowns.

## Autonomous Strategy Synthesis & Research Safety Invariants
1. **Zero Execution Authority**: Research agents and factor engines operate exclusively as analytical tools. They cannot create live orders or access exchange credentials.
2. **Deterministic Validation Gating**: Candidate strategies synthesized by research agents cannot be deployed directly. They require backtest Sharpe verification and positive Walk-Forward Efficiency (WFE) validation.
3. **Immutable Strategy Versioning**: Approved strategy candidates are stored as immutable `StrategyVersion` records with full parameter provenance.

## Real-Time Streaming & WebSocket Safety Invariants
1. **Read-Only Subscriptions**: Connected WebSocket clients have zero command or order execution capability. Any inbound `SEND` command to broadcast topics is intercepted and rejected.
2. **Decoupled Asynchronous Processing**: Real-time tick ingestion is handled on an asynchronous in-memory event bus (`MarketEventBus`) ensuring high-throughput market feeds never block or starve order execution or reconciliation workers.
3. **Paper/Demo Feeds Only**: Market data streaming connectors stream exclusively from verified paper/demo endpoints.

## Backtesting & Quantitative Simulation Invariants
1. **Isolated Sandbox**: Backtesting simulation runs entirely offline and in-memory/database without network connection to broker trading endpoints.
2. **Realistic Friction Enforced**: Slippage and fee modeling are mandatory parameters in simulation to prevent ungrounded or curve-fitted equity returns.
3. **Walk-Forward Validation**: Multi-window In-Sample / Out-Of-Sample analysis validates parameter stability before strategy deployment.

## Execution Gateway Safety Invariants
1. **No Direct Execution**: Neither bots, agents, strategies, nor UI can interact directly with broker adapters. All execution MUST flow through `ExecutionGateway` which enforces persisted risk approval.
2. **Bot Operational State Check**: `ExecutionGateway` strictly refuses to dispatch orders for bots in `PAUSED`, `STOPPED`, or `EMERGENCY_STOPPED` states.
3. **Idempotency & Lifecycle Sync**: Order submission advances order lifecycle state to `SUBMITTED` / `ACKNOWLEDGED` with exchange order ID, recorded atomically with order events.

## Reconciliation Safety Invariants
1. **Automated Mismatch Response**: Any critical discrepancy between local ledger state and broker state (balances, open orders, fills, positions) causes the affected running bot to be paused immediately and blocks new order submissions.
2. **Explicit Recovery**: A bot paused due to reconciliation discrepancy CANNOT automatically resume or self-heal when a subsequent check passes. It requires:
   - A subsequent reconciliation run that confirms state is `MATCHED`.
   - An explicit operator recovery command via `POST /api/reconciliation/recover`.
   - Passing deterministic risk engine checks.
3. **Emergency Stop Protection**: If a bot is `EMERGENCY_STOPPED`, ordinary resume requests and standard reconciliation recoveries are strictly rejected. Recovery from emergency stop requires a dedicated emergency reset workflow.
4. **Append-Only Audit Trail**: Every reconciliation run, mismatch detection, bot pause, execution dispatch, and recovery event is recorded to immutable audit storage.
