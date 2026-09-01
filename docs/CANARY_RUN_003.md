# 12-Hour Autonomous Paper Canary Run Preparation: ALPACA-PAPER-003

## 1. Run Metadata (Prepared Template)
- **Run Identifier**: `CANARY-ALPACA-12H-003`
- **Execution Mode**: `PAPER_AUTONOMOUS`
- **Provider**: `ALPACA` (Paper API)
- **Target Planned Duration**: **12 Hours Continuous Execution**
- **Strategy & Bot**: `USER_CONFIGURED` (To be created and deployed by operator)
- **Current Lifecycle State**: `CONFIGURATION_REQUIRED` (Clean Slate)
- **Live Trading Invariant**: `LIVE_TRADING_DISABLED = true` (Strictly Enforced)

---

## 2. Operator Pre-Flight Checklist
- [ ] Local database reset verified via `GET /api/reset/preflight` (0 bots, 0 orders, 0 positions).
- [ ] Alpaca Paper broker balance synchronized and verified clean (0 open positions, 0 open orders).
- [ ] Strategy created and versioned via UI / API (`/api/strategies`).
- [ ] Risk limits configured (max position size, max daily drawdown, order frequency caps).
- [ ] Exactly ONE bot deployed in `PAPER_AUTONOMOUS` mode (`/api/bots`).
- [ ] Autonomous loop started (`POST /api/canary/start`).
