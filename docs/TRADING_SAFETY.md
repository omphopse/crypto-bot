# Trading safety

ALGOPILOT must fail closed. The deterministic risk engine is the final order authority and evaluates balance, buying power, per-symbol and portfolio exposure, leverage, daily loss, drawdown, consecutive loss, trade frequency, price freshness, spread, slippage, duplicates, strategy state, bot state, and kill-switch state.

## Permitted Trading Modes
- Alpaca: `PAPER` only (`https://paper-api.alpaca.markets/v2`)
- Bybit: `DEMO` only (`https://api-demo.bybit.com`)
- `LIVE`: Strictly disabled and rejected at all configuration and execution boundaries.

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
