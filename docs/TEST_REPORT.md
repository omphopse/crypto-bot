# Test report

## 2026-08-31 — Exchange Adapters & Execution Gateway Milestone

Command: `mvn test -q`

Result: passed (75 tests executed, 0 failures, 0 errors, 0 skipped).

### Covered Exchange Adapter & Execution Scenarios:

1. **Alpaca Paper Adapter:**
   - Order submission with mapped client order ID, normalized symbol, and acknowledged status.
   - Order cancellation request to Alpaca paper endpoint.
   - Account balance retrieval and normalization (cash, equity, buying power).
   - Open orders normalization.
   - Open positions normalization with directional signs.
   - Rejection of live trading mode (`LIVE_TRADING_DISABLED`).
   - Rejection of live API base URL configuration.

2. **Bybit Demo Adapter:**
   - Order placement with linear category, orderLinkId, and market execution.
   - Order cancellation via Bybit V5 endpoint.
   - Unified wallet balance normalization (totalEquity, walletBalance, available).
   - Linear position list normalization with direction and unrealised P&L.
   - Execution/fill history normalization.
   - HMAC-SHA256 request signing validation.
   - Rejection of live Bybit API endpoints and LIVE mode.

3. **Execution Gateway:**
   - Risk-approved order dispatch for running bot to matching broker adapter.
   - Automatic order lifecycle advancement (`SUBMITTED` / `ACKNOWLEDGED`).
   - Append-only audit record generation on dispatch and cancellation.
   - Rejection of order dispatch when bot is in `PAUSED` status.
   - Rejection of order dispatch when bot is in `LIVE` mode.
   - Rejection of dispatch when order is not in `CREATED` state.

4. **Composite Broker State Provider:**
   - Correct routing of reconciliation queries to active broker adapters.
   - Rejection of unsupported or LIVE broker configurations.

### Earlier Verified Milestone Suites (All Passing):
- Reconciliation & Recovery engine, service, controller, and health indicators (54 tests).
- Deterministic Risk Engine evaluation (exposure, daily loss, drawdown, stale data, frequency, duplicate orders).
- Idempotent order store and lifecycle state machine.
- Fill ingestion and position accounting (average entry, realized P&L).
- Bot controls (pause, stop, resume, emergency stop guard).
- Strategy immutability and versioning.
- Typed agent decision journaling.
