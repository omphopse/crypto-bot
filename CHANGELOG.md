# Changelog

## 0.3.0 — 2026-08-31

- Added non-bypassable `ExecutionGateway` that validates bot execution state, enforces `LIVE_TRADING_DISABLED`, dispatches orders to registered broker adapters, updates order lifecycle states, and records append-only audit events.
- Added `AlpacaPaperAdapter` implementing `BrokerOrderAdapter` and `BrokerStateProvider` for Alpaca Paper Trading (`https://paper-api.alpaca.markets/v2`).
- Added `BybitDemoAdapter` implementing `BrokerOrderAdapter` and `BrokerStateProvider` with HMAC-SHA256 request signing for Bybit Demo Trading (`https://api-demo.bybit.com`).
- Added `CompositeBrokerStateProvider` delegating reconciliation queries to active broker adapters.
- Added operational endpoints under `/api/execution` (`/dispatch/{orderId}`, `/cancel/{orderId}`, `/adapters`).
- Added strict credential isolation ensuring AI and browser components cannot access API keys or secrets.
- Added automated test suite covering order dispatching, cancellation, payload normalization, HMAC signing, and live-trading rejection (75 total passing tests).

## 0.2.0 — 2026-08-31

- Added modular broker-state abstraction (`BrokerStateProvider`) and canonical models (`BrokerAccountBalance`, `BrokerOrder`, `BrokerFill`, `BrokerPosition`, `BrokerStateSnapshot`).
- Added deterministic side-effect-free `ReconciliationEngine` comparing local ledger state against broker state across balances, open orders, fills, and positions.
- Added deterministic mismatch classification with typed categories (`BALANCE_MISMATCH`, `ORDER_MISMATCH`, `FILL_MISMATCH`, `POSITION_MISMATCH`), subcategories, and severity scoring (`CRITICAL`, `WARNING`, `INFO`).
- Added Flyway migration `V8__reconciliation.sql` and PostgreSQL persistence (`JdbcReconciliationStore`) for append-only reconciliation runs, mismatch history, and recovery records.
- Added automated bot safety response: critical discrepancies pause affected running bots, block new order creation, and record critical audit logs.
- Added explicit multi-step recovery workflow (`ReconciliationRecoveryService`) requiring validated matching state before a paused bot can resume.
- Added emergency-stop invariants ensuring emergency-stopped bots cannot resume or recover through ordinary reconciliation workflows.
- Added REST APIs under `/api/reconciliation` for runs, run details, unresolved mismatches, bot status, manual execution, and recovery.
- Added Spring Boot Actuator health indicator (`ReconciliationHealthIndicator`) distinguishing active critical discrepancies from historical resolved events.
- Integrated State Reconciliation & Recovery view and interactive controls into the operations console frontend.
- Added comprehensive unit and integration test suite covering all 16 required reconciliation and recovery scenarios.

## 0.1.0 — 2026-08-31

- Added the ALGOPILOT paper-mode operations console.
- Added Spring Boot API foundation with actuator health checks and Flyway/PostgreSQL configuration.
- Added a typed deterministic risk gate covering order duplication, stop state, data freshness, exposure, daily loss, drawdown, execution quality, and trade-frequency limits.
- Added risk-engine tests and container deployment definitions.
- Added idempotent internal order creation and append-only audit events; rejected decisions cannot create orders.
- Added immutable strategy version creation with audit events and a strategy lifecycle schema.
- Added paper/demo-only bot deployment with persisted lifecycle state and live-trading rejection at the API boundary.
- Added typed agent decision journaling with evidence classification and no direct execution capability.
- Persisted every risk evaluation with its typed request snapshot, deterministic reasons, and audit event.
- Added an audited order lifecycle state machine that rejects invalid and terminal-state transitions.
- Persisted append-only order events alongside global lifecycle audit events.
- Added idempotent fill ingestion and position accounting for average entry and realized P&L.
- Added persisted bot pause, stop, resume, and emergency-stop controls with an emergency-resume guard.
