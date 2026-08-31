# Architecture

## Service boundary

The deployable system is a Spring Boot service with PostgreSQL/Flyway, Redis for idempotency and stream coordination, a web operations console, and broker adapters for Alpaca Paper and Bybit Demo. It exposes REST resources under `/api` and actuator operational health endpoints under `/actuator`.

```text
Market adapters + research gateway
          ↓
Event bus → strategy evaluation → typed agent decision
          ↓                         ↓
Portfolio state ─────────── deterministic risk engine
                                        ↓ approved only
                             idempotent execution gateway
                                        ↓
                         Alpaca Paper / Bybit Demo adapters
                                        ↕
                   Deterministic Reconciliation Engine
                                        ↓
PostgreSQL audit journal + Reconciliation store + Actuator Health
```

## Critical invariants

1. A broker adapter accepts only an approved `OrderRecord` with an approved `RiskDecision` plus a unique `clientOrderId`.
2. AI and web research never receive secrets or broker execution permissions.
3. Every risk evaluation persists its input snapshot and reason-coded result before order creation can continue.
4. Every decision, rejection, order transition, fill, execution dispatch, reconciliation mismatch, and stop action is append-only audited.
5. A reconciliation mismatch automatically pauses the affected running bot and blocks new orders; recovery requires an explicit post-reconciliation verification and operator recovery command.
6. A deployed strategy version is immutable. Changes create a new version and deployment record. The current API provides `POST /api/strategies` and `POST /api/strategies/{strategyId}/versions` for these append-only definitions.
7. Emergency-stopped bots cannot resume through ordinary controls or through reconciliation matching alone; they require dedicated emergency-recovery procedures.
8. Live trading remains disabled and rejected across all API and adapter boundaries.

## Execution Gateway & Broker Adapters

- **Execution Gateway (`ExecutionGateway`)**: Central router ensuring orders are dispatched ONLY when the bot is in `RUNNING` status, rejecting `LIVE` mode, dispatching to the registered adapter, advancing order lifecycle state to `SUBMITTED` / `ACKNOWLEDGED`, and writing immutable audit records.
- **Alpaca Paper Adapter (`AlpacaPaperAdapter`)**: REST client for Alpaca Paper (`https://paper-api.alpaca.markets/v2`) providing order placement, cancellation, balance, position, and fill retrieval.
- **Bybit Demo Adapter (`BybitDemoAdapter`)**: REST client for Bybit V5 Demo (`https://api-demo.bybit.com`) with HMAC-SHA256 authenticated order execution and position tracking.
- **Composite Broker State Provider (`CompositeBrokerStateProvider`)**: Routes reconciliation snapshot queries to the active adapter based on bot broker and mode.

## Reconciliation & Recovery Subsystem

The reconciliation architecture is composed of:
- **Broker State Provider Abstraction (`BrokerStateProvider`)**: Canonical adapter interface normalizing external broker account balances, open orders, executions/fills, and positions without leaking broker-specific structures into domain logic.
- **Deterministic Reconciliation Engine (`ReconciliationEngine`)**: Pure, side-effect-free evaluator that compares local ledger state with broker snapshot across 4 dimensions:
  1. *Account Balances* (cash, equity, buying power)
  2. *Open Orders* (clientOrderId mapping, order status, quantities, prices)
  3. *Fills* (exchangeFillId deduplication, fill prices, fees)
  4. *Positions* (symbol matching, quantities, directional side, average entry prices)
- **Persistence & Audit (`JdbcReconciliationStore`)**: Append-only records for `reconciliation_runs`, `reconciliation_mismatches`, and `reconciliation_recoveries`.
- **Operational Health (`ReconciliationHealthIndicator`)**: Distinguishes current active critical discrepancies from historical resolved events for Spring Actuator health monitoring.

## Persistence

Flyway migrations create domain tables: audit events, risk decisions, orders, order events, strategies, strategy versions, bots, agent decisions, fills, positions, reconciliation runs, reconciliation mismatches, and recovery records. High-frequency state is indexed by `bot_id`, `strategy_version_id`, `symbol`, `status`, and descending occurrence timestamp.
