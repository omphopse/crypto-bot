# Clean Reset Runbook & Pre-Flight Operator Guide

## 1. Reset Execution Procedure

### Step 1: Execute Reset Confirmation
Run the confirmation endpoint with the explicit safety token:
```bash
curl -X POST "http://localhost:8080/api/reset/confirm?confirmationString=RESET_ALGOPILOT_EXPERIMENT_STATE&operator=OPERATOR"
```

### Step 2: Verify Pre-Flight System Status
Verify that local state has zero active bots, orders, or positions:
```bash
curl -s http://localhost:8080/api/reset/preflight | jq .
```

Expected Response:
```json
{
  "localDatabaseClean": true,
  "redisClean": true,
  "activeBots": 0,
  "localOrders": 0,
  "localFills": 0,
  "localPositions": 0,
  "localTrades": 0,
  "agentSessions": 0,
  "agentDecisions": 0,
  "researchRecords": 0,
  "strategyCandidates": 0,
  "canaryRuns": 0,
  "brokerBalance": 100000.00,
  "brokerOpenOrders": 0,
  "brokerOpenPositions": 0,
  "brokerStatus": "CLEAN",
  "systemMode": "CONFIGURATION_REQUIRED",
  "liveTradingDisabled": true
}
```

---

## 2. Alpaca Paper Account Cleanliness Invariant
- **No Automatic Broker Liquidation**: The local database reset does NOT alter broker state.
- **Broker Activity Invariant**: If the Alpaca Paper account contains open positions or pending orders, `preflight` flags `BROKER_NOT_CLEAN` and refuses autonomous deployment.
- **Provider Account Options**:
  1. Manually close positions/cancel orders in the Alpaca Paper dashboard.
  2. Create a fresh Alpaca Paper account keypair.
