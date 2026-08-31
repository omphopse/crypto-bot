# ALGOPILOT Operational Runbook & Incident Response Manual

This document defines standard operating procedures, failure modes, and recovery protocols for ALGOPILOT operations.

---

## 1. System Health & Monitoring

### Health Check Probes
- **Overall Health**: `GET /actuator/health`
- **Liveness Probe**: `GET /actuator/health/liveness`
- **Readiness Probe**: `GET /actuator/health/readiness`
- **Prometheus Metrics Scrape**: `GET /actuator/prometheus`

### Key Operational Metrics
| Metric Name | Type | Description | Alert Threshold |
| :--- | :--- | :--- | :--- |
| `algopilot_reconciliation_mismatches_active` | Gauge | Active reconciliation discrepancies | `> 0` for $> 60\text{s}$ |
| `algopilot_risk_decisions_count{status="REJECTED"}` | Counter | Orders rejected by deterministic risk engine | Rate $> 5/\text{min}$ |
| `algopilot_orders_submitted_count` | Counter | Total orders evaluated | Rate anomaly |
| `algopilot_orders_executed_count` | Counter | Total orders dispatched to broker | Rate anomaly |
| `algopilot_rebalance_runs_count` | Counter | Total portfolio rebalancing runs | Expected periodic |

---

## 2. Incident Level 1: Reconciliation Discrepancy & Operator Recovery

### Symptom
- Health indicator returns `status: DOWN` with `reconciliation: {status: OUT_OF_SYNC}`.
- Affected bot state transitions automatically to `PAUSED`.
- New orders for the affected bot are categorically rejected.

### Recovery Procedure
1. **Inspect Active Discrepancies**:
   ```sh
   curl -s http://localhost:8080/api/reconciliation/mismatches | jq .
   curl -s http://localhost:8080/api/reconciliation/status/{botId} | jq .
   ```
2. **Verify Broker Account State**:
   - Log in to Alpaca Paper or Bybit Demo dashboard.
   - Inspect actual open orders, fills, and position quantities.
3. **Trigger On-Demand Reconciliation**:
   ```sh
   curl -X POST http://localhost:8080/api/reconciliation/run \
     -H "Content-Type: application/json" \
     -d '{"botId":"<BOT_UUID>"}'
   ```
4. **Execute Formal Operator Recovery**:
   - Once reconciliation run confirms state is `MATCHED`, execute recovery:
   ```sh
   curl -X POST http://localhost:8080/api/reconciliation/recover \
     -H "Content-Type: application/json" \
     -d '{"botId":"<BOT_UUID>","resolutionNotes":"Verified ledger matched broker state"}'
   ```
5. **Verify Bot Resumed**:
   - Check bot status via `GET /api/bots/{botId}` (must be `RUNNING`).

---

## 3. Incident Level 2: Emergency Stop Procedure & Post-Mortem Audit

### Symptom
- Market volatility spike, exchange connectivity outage, or critical operational anomaly requires immediate cessation of trading.

### Trigger Emergency Stop
```sh
curl -X POST http://localhost:8080/api/bots/{botId}/emergency-stop
```

### Invariant Rules
- **Non-Bypassable Lock**: An `EMERGENCY_STOPPED` bot cannot be resumed via standard `/resume` or standard reconciliation recovery `/recover`.
- All open orders for the bot are cancelled.

### Post-Mortem & Reset Procedure
1. Verify audit trail of all actions prior to emergency stop:
   ```sh
   curl -s http://localhost:8080/api/audit/events?entityId={botId} | jq .
   ```
2. Reconcile complete fill history and open positions.
3. Once the environment is deemed safe, redeploy a new bot instance against the strategy version.

---

## 4. Incident Level 3: WebSocket Stream Disconnection & Reconnection

### Symptom
- Client UI stream disconnects or tick arrival timestamp lags $> 30\text{s}$.

### Diagnostics & Recovery
1. **Check Feed Status**:
   ```sh
   curl -s http://localhost:8080/api/feed/status | jq .
   ```
2. **Resubscribe Symbols**:
   ```sh
   curl -X POST http://localhost:8080/api/feed/subscribe \
     -H "Content-Type: application/json" \
     -d '{"broker":"ALPACA_PAPER","symbols":["BTC/USD","ETH/USD"]}'
   ```
3. Check WebSocket connection state at `ws://localhost:8080/ws`.

---

## 5. Incident Level 4: Risk Gate Tripwire & Portfolio Rebalancing

### Symptom
- Spike in `algopilot_risk_decisions_count{status="REJECTED"}`.
- Max drawdown or exposure limit reached.

### Procedure
1. Check recent risk decisions and specific rejection reason codes:
   ```sh
   curl -s http://localhost:8080/api/risk/evaluations/recent | jq .
   ```
2. Evaluate current portfolio drift:
   ```sh
   curl -X POST http://localhost:8080/api/portfolio/rebalance/evaluate-drift \
     -H "Content-Type: application/json" \
     -d '{"planId":"<PLAN_UUID>","driftThresholdPct":5.0}'
   ```
3. Execute scheduled/manual risk-parity rebalance:
   ```sh
   curl -X POST http://localhost:8080/api/portfolio/rebalance/execute \
     -H "Content-Type: application/json" \
     -d '{"planId":"<PLAN_UUID>","botId":"<BOT_UUID>","driftThresholdPct":5.0}'
   ```

---

## 6. Incident Level 5: Zero-Downtime Migration & Rollback Checklist

1. **Pre-Deployment Backup**:
   ```sh
   docker compose exec postgres pg_dump -U algopilot algopilot > backup_$(date +%Y%m%d_%H%M%S).sql
   ```
2. **Flyway Migration Execution**:
   - Migrations `V1` through `V12` run automatically on container startup.
3. **Rollback Checklist**:
   - If a deployment failure occurs, rollback application container image to prior tag while preserving database integrity.
