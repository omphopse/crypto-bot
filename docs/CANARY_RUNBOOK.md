# Operational Canary Runbook: Alpaca Paper & Bybit Demo

## 1. Prerequisites
- Verify database connectivity: `docker compose ps` / PostgreSQL healthy on port 5432.
- Verify environment variables:
  - `ALPACA_PAPER_KEY_ID` & `ALPACA_PAPER_SECRET_KEY`
  - `BYBIT_DEMO_API_KEY` & `BYBIT_DEMO_API_SECRET`
  - `LIVE_TRADING_DISABLED=true` (MUST NOT BE OVERRIDDEN)

## 2. Starting the Autonomous Canary Loop
1. Verify system health via REST:
   `GET /api/health` ➔ status `UP`.
2. Inspect active canary bots:
   `GET /api/canary/status`
3. Start the continuous autonomous runner:
   `POST /api/canary/start`
4. Confirm runner execution in logs:
   - `AUTONOMOUS_BOT_RUNNER_STARTED`
   - `LEASE_ACQUIRED`
   - `AUTONOMOUS_CYCLE_COMPLETED`

## 3. Emergency Interventions
- **Immediate Pause of a Canary Bot**:
  `POST /api/bots/{botId}/pause`
- **Global Emergency Stop**:
  `POST /api/emergency/kill` ➔ Instantly terminates order creation, pauses bots, and dispatches market exit orders.
