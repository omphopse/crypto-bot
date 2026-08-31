# Trading safety

ALGOPILOT must fail closed. The deterministic risk engine is the final order authority and evaluates balance, buying power, per-symbol and portfolio exposure, leverage, daily loss, drawdown, consecutive loss, trade frequency, price freshness, spread, slippage, duplicates, strategy state, bot state, and kill-switch state.

## Permitted Trading Modes
- Alpaca: `PAPER` only (`https://paper-api.alpaca.markets/v2`)
- Bybit: `DEMO` only (`https://api-demo.bybit.com`)
- `LIVE`: Strictly disabled and rejected at all configuration and execution boundaries.

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
