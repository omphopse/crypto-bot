# Security Architecture & Boundaries

## Credential Isolation
1. **Zero AI/Browser Access**: AI, LLMs, browser, and research components NEVER receive exchange credentials, API keys, secrets, or execution permissions.
2. **No Secret Exposure in APIs**: Reconciliation, bot, order, and audit endpoints never expose API keys or credentials.
3. **No Withdrawal Permissions**: Configured API credentials must be paper/demo keys without fund-withdrawal capabilities.

## Execution Authority Separation
- Research / Web / AI = Untrusted Information Source (generates structured signals/journaled decisions only).
- Risk Engine = Deterministic Authority (pure boolean/reason gate evaluating exposure, limits, drawdown, and kill switches).
- Execution Service = Idempotent Broker Gateway (submits orders only with approved RiskDecision and unique client order ID).
- Broker State Provider = Canonical Read-Only Abstraction (reconciliation and balance inspection without live order submission authority).

## Data & Audit Integrity
- Audit events are strictly append-only in PostgreSQL.
- Risk decisions snapshot input parameters and evaluated reasons before order persistence.
- Order events and reconciliation runs preserve complete historical state transitions without overwriting.
