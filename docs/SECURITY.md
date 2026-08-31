# Security Architecture & Boundaries

## Credential Isolation
1. **Zero AI/Browser Access**: AI, LLMs, browser, and research components NEVER receive exchange credentials, API keys, secrets, or execution permissions.
2. **No Secret Exposure in APIs**: Adapter, backtest, reconciliation, bot, order, and audit endpoints never expose API keys, secrets, or authorization signatures.
3. **Environment-Only Configuration**: Exchange credentials (`ALPACA_API_KEY`, `ALPACA_API_SECRET`, `BYBIT_API_KEY`, `BYBIT_API_SECRET`) are loaded exclusively from environment variables into backend adapter components.
4. **No Withdrawal Permissions**: Configured API credentials must be paper/demo keys without fund-withdrawal capabilities.

## Execution Authority Separation
- Research / Web / AI = Untrusted Information Source (generates structured signals/journaled decisions only).
- Risk Engine = Deterministic Authority (pure boolean/reason gate evaluating exposure, limits, drawdown, and kill switches).
- Backtesting Engine = Historical Simulation Sandbox (evaluates quantitative indicators and metrics without live network access or trade execution authority).
- Execution Gateway = Idempotent Broker Router (submits orders only with approved RiskDecision, running bot status, and unique client order ID).
- Broker Adapters = Paper / Demo Execution Clients (strictly bound to `paper-api.alpaca.markets` and `api-demo.bybit.com`).
- Broker State Provider = Canonical Read-Only Abstraction (reconciliation and balance inspection without live order submission authority).

## Data & Audit Integrity
- Audit events are strictly append-only in PostgreSQL.
- Risk decisions snapshot input parameters and evaluated reasons before order persistence.
- Order events, execution dispatch events, reconciliation runs, and backtest results preserve complete historical records without overwriting.
