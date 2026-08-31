# Test report

## 2026-08-31 — Reconciliation & Recovery Milestone

Command: `mvn test -q`

Result: passed (54 tests executed, 0 failures, 0 errors, 0 skipped).

### Covered Reconciliation & Safety Scenarios:

1. **Deterministic State Matching:**
   - Identical local ledger and normalized broker snapshot returns `MATCHED` run with zero discrepancies.
2. **Balance Discrepancy Detection:**
   - Cash and equity variations are detected and classified as `BALANCE_MISMATCH` with `CRITICAL` severity.
3. **Open Order Discrepancy Detection:**
   - Local order missing on broker detected as `LOCAL_ORDER_MISSING_BROKER_ORDER` (`CRITICAL`).
   - Broker order missing locally detected as `BROKER_ORDER_MISSING_LOCAL_ORDER` (`CRITICAL`).
   - Quantity mismatch detected as `ORDER_QUANTITY_MISMATCH` (`CRITICAL`).
   - Status mismatch detected as `ORDER_STATUS_MISMATCH` (`CRITICAL`).
   - Price discrepancy detected as `ORDER_PRICE_MISMATCH` (`WARNING`).
4. **Fill / Execution Discrepancy Detection:**
   - Fill missing locally detected as `FILL_MISSING_LOCALLY` (`CRITICAL`).
   - Fill missing on broker detected as `FILL_MISSING_BROKER` (`CRITICAL`).
   - Fill quantity, price, and fee discrepancies classified with appropriate severities.
5. **Position Discrepancy Detection:**
   - Position quantity discrepancy detected as `POSITION_QUANTITY_MISMATCH` (`CRITICAL`).
   - Opposing direction (LONG vs SHORT) detected as `POSITION_SIDE_MISMATCH` (`CRITICAL`).
   - Average entry price discrepancy detected as `POSITION_PRICE_MISMATCH` (`WARNING`).
6. **Multi-Discrepancy Aggregation:**
   - Simultaneous mismatches across balance, orders, fills, and positions are all captured in a single run.
7. **Automated Bot Safety Response:**
   - Critical reconciliation mismatch automatically pauses a running bot (`BotStatus.PAUSED`).
   - Paused bot status blocks subsequent order creation in `RiskEngine` / `OrderService`.
   - Critical audit events (`BOT_PAUSED_RECONCILIATION`, `RECONCILIATION_MISMATCH_CRITICAL`) are persisted.
8. **Explicit Recovery Workflow:**
   - Resolved / matching state requires explicit operator recovery request before clearing discrepancies.
   - Recovery on un-matched state is strictly rejected.
   - Recovery completed event is logged to immutable audit trail.
9. **Emergency Stop Invariant:**
   - Emergency-stopped bot CANNOT resume or recover simply because reconciliation is matched.
   - Requires dedicated emergency recovery reset procedures.
10. **Provider Failure Handling:**
    - External broker communication failure is marked as `FAILED` run, pauses running bot, and records audit event.
11. **Health Indicator Integration:**
    - Actuator health reports `DOWN` when active unresolved critical mismatches exist, and `UP` when healthy.
12. **Live Trading Protection:**
    - Live trading mode is strictly rejected at bot deployment and reconciliation execution boundaries.
13. **REST Controller API:**
    - `/api/reconciliation/run`, `/runs`, `/runs/{id}`, `/mismatches`, `/status/{botId}`, and `/recover` validate inputs and return expected response codes and payloads.

### Earlier Safety Boundary Scenarios (Verified & Passing):
- Safe typed order approval.
- Duplicate order with stale market data rejection.
- Emergency stop hard rejection.
- Idempotent order creation retry.
- Rejected risk decision order isolation.
- Sequential immutable strategy versions.
- Alpaca paper / Bybit demo mode enforcement.
- Structured agent decision journal validation.
- Order lifecycle state machine valid/invalid transitions.
- Order event append-only recording.
- Position accounting average entry and realized P&L.
- Ordinary paused bot resume vs emergency-stop guard.
