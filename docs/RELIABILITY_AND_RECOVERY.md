# Operational Reliability, Heartbeat Watchdog & Automated Recovery

## 1. Core Safety Principle: Fail Safe
When uncertain: **STOP NEW TRADING**.
The system never assumes a component is healthy without verified heartbeats, and never automatically resumes trading upon process restart without explicit verification.

## 2. Heartbeat Architecture
Heartbeats are collected across 10 critical subsystem components:
- `APPLICATION`: JVM runtime and health probe state.
- `AGENT_SESSION`: Active autonomous decision session lifecycle.
- `BOT_RUNTIME`: Individual bot execution loops.
- `MARKET_DATA`: Stream freshness and candle ingestion feeds.
- `BROKER_CONNECTION`: Provider API accessibility (Alpaca Paper / Bybit Demo).
- `POSITION_MONITOR`: Surveillance over open risk and trailing stops.
- `RECONCILIATION`: State matching between local orders/positions and broker accounts.
- `EXECUTION_PIPELINE`: Order dispatch gateway health.
- `WATCHDOG`: Self-monitoring watchdog agent.
- `DATABASE`: PostgreSQL connection pool liveness.

## 3. Distributed Lease Coordination
- `LeaseManager` utilizes PostgreSQL row-level locks and unique constraints (`bot_runtime_leases`) to prevent split-brain execution across multiple application instances.
- Only one instance can hold an active lease on a given bot at any time.
- If a lease expires, the bot is automatically paused.

## 4. Centralized Watchdog Service
- Periodically checks heartbeats, lease validity, market data freshness, and in-flight orders.
- Flags:
  - `BOT_STALE`: Bot heartbeat age $> 30,000$ms ➔ bot paused.
  - `RUNTIME_LEASE_LOST`: Instance lease expired ➔ bot paused.
  - `ORDER_STUCK`: Order in `CREATED` or `SUBMITTED` state $> 30,000$ms ➔ bot paused and reconciliation triggered.
  - `MARKET_DATA_STALE`: Quote/candle age $> 60,000$ms ➔ new entries blocked.

## 5. Automated Recovery Protocol
- Deterministic sequence:
  `START` ➔ `PAUSE_BOT` ➔ `RECONCILE_WITH_BROKER` ➔ `VALIDATE_RISK` ➔ `COMPLETED` (ready for operator resume).
- If reconciliation detects unresolved critical mismatches, recovery fails (`RECOVERY_FAILED`) and the bot remains locked in `PAUSED` state.
