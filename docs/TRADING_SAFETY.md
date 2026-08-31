# Trading safety

ALGOPILOT must fail closed. The deterministic risk engine is the final order authority and evaluates balance, buying power, per-symbol and portfolio exposure, leverage, daily loss, drawdown, consecutive loss, trade frequency, price freshness, spread, slippage, duplicates, strategy state, bot state, and kill-switch state.

## Permitted Trading Modes
- Alpaca: `PAPER` only
- Bybit: `DEMO` only
- `LIVE`: Strictly disabled and rejected at all API boundaries.

## Reconciliation Safety Invariants
1. **Automated Mismatch Response**: Any critical discrepancy between local ledger state and broker state (balances, open orders, fills, positions) causes the affected running bot to be paused immediately and blocks new order submissions.
2. **Explicit Recovery**: A bot paused due to reconciliation discrepancy CANNOT automatically resume or self-heal when a subsequent check passes. It requires:
   - A subsequent reconciliation run that confirms state is `MATCHED`.
   - An explicit operator recovery command via `POST /api/reconciliation/recover`.
   - Passing deterministic risk engine checks.
3. **Emergency Stop Protection**: If a bot is `EMERGENCY_STOPPED`, ordinary resume requests and standard reconciliation recoveries are strictly rejected. Recovery from emergency stop requires a dedicated emergency reset workflow.
4. **Append-Only Audit Trail**: Every reconciliation run, mismatch detection, bot pause, and recovery event is recorded to immutable audit storage.
