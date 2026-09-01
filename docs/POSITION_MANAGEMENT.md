# Autonomous Position Management & Dynamic Exits

## 1. Position Lifecycle States
Open positions progress through strict lifecycle states:
- `OPENING`: Order dispatched to exchange.
- `OPEN`: Initial fill confirmed.
- `MONITORING`: Position under continuous multi-metric surveillance.
- `REDUCE_PENDING`: Partial take profit or risk reduction order in-flight.
- `CLOSING`: Exit order dispatched to broker.
- `CLOSED`: Position fully liquidated and reconciled.
- `ERROR` / `RECOVERY_REQUIRED`: Mismatch detected during monitoring or reconciliation.

## 2. Deterministic Exit Priority
The position monitoring loop evaluates exit conditions in strict deterministic precedence:
1. **`EMERGENCY_STOP`**: Global operator kill switch or bot emergency stop.
2. **`RECONCILIATION_SAFETY`**: Critical unresolved broker state discrepancies.
3. **`HARD_STOP_LOSS`**: Price breaches stop loss boundary ($P \le \text{stop}$ for long).
4. **`RISK_LIMIT`**: Portfolio or symbol drawdown limits exceeded.
5. **`STRATEGY_INVALIDATION`**: Entry premise invalidated (e.g. RSI momentum collapse).
6. **`TAKE_PROFIT`**: Fixed or partial target price achieved ($P \ge \text{target}$ for long).
7. **`TRAILING_STOP`**: Ratchet trailing stop breached ($P \le \text{HWM} \times (1 - \text{trailPct})$).
8. **`AI_POSITION_DECISION`**: LLM reasoner thesis exit proposal (`CLOSE` or `REDUCE`).

> [!IMPORTANT]
> **Safety Invariant**: An advisory AI decision (such as `HOLD`) cannot override higher-priority deterministic safety triggers (e.g. `HARD_STOP_LOSS` or `STRATEGY_INVALIDATION`).

## 3. Dynamic Stop Loss & Trailing Stops
- **Non-Invertible Stop Movement**: A stop loss can ONLY move in the direction that reduces downside risk ($newStop > oldStop$ for long positions). Moves that increase risk or place stops beyond the current market price are unconditionally rejected.
- **High Water Mark Ratchet**: `TrailingStopManager` continuously updates the high-water mark and ratchets the trailing stop upward.

## 4. Execution & Post-Exit Reconciliation
- Exit orders are submitted exclusively through `OrderService` (evaluated by `RiskEngine`) and dispatched via `ExecutionGateway`.
- Following every position exit execution, `ReconciliationService.reconcile(...)` verifies that local position quantities and broker account positions reach zero.
