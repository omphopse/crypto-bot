# Test report

## 2026-08-31 — Cross-Asset Portfolio Allocation & Risk Parity Engine Milestone

Command: `mvn test -q`

Result: passed (114 tests executed across 40 test classes, 0 failures, 0 errors, 0 skipped).

### Covered Portfolio Allocation & Risk Parity Scenarios:

1. **Correlation & Covariance Engine (`CorrelationMatrixCalculator`):**
   - Percentage returns extraction from price candles.
   - Sample mean, variance, standard deviation calculations.
   - Pairwise covariance calculation.
   - Pearson correlation coefficients bounded strictly between `[-1.0000, +1.0000]`.
   - Complete cross-asset correlation matrix construction.

2. **Risk Parity Allocator (`RiskParityAllocator`):**
   - Inverse-volatility weighting assigning lower weights to high-volatility assets.
   - Exact sum-to-1.0000 normalization invariant.
   - Handling of uniform/zero volatility fallback.

3. **Portfolio Risk Calculator (`PortfolioRiskCalculator`):**
   - Portfolio variance $\sigma_p^2 = \sum_i \sum_j w_i w_j \sigma_i \sigma_j \rho_{ij}$ and portfolio volatility $\sigma_p$.
   - 1-day 95% Parametric Value at Risk (VaR 95%).
   - 1-day 95% Conditional Value at Risk (Expected Shortfall / CVaR 95%).

4. **Portfolio Allocation Service (`PortfolioAllocationService`):**
   - End-to-end plan generation with rebalancing delta calculations.
   - Validation against empty symbol lists.
   - PostgreSQL persistence and append-only audit trail logging.

5. **Portfolio Allocation REST Controller (`PortfolioAllocationController`):**
   - Endpoint `/api/portfolio/allocation/allocate` generating plans.
   - Endpoint `/api/portfolio/allocation/latest` and `/{id}` with 404 handling.
   - Endpoint `/api/portfolio/allocation/history`.

### Earlier Verified Milestone Suites (All Passing):
- Autonomous Multi-Factor Strategy & Research Agent Engine (6 tests).
- Real-Time WebSocket Streaming & Market Data Feeds (12 tests).
- Event-Driven Backtesting & Walk-Forward Validation Engine (14 tests).
- Exchange Adapters (Alpaca Paper & Bybit Demo) and Execution Gateway (21 tests).
- Reconciliation & Recovery engine, service, controller, and health indicators (54 tests).
- Deterministic Risk Engine evaluation.
- Idempotent order store and lifecycle state machine.
- Fill ingestion and position accounting.
- Bot operational controls and emergency stop guard.
- Strategy immutability and versioning.
- Typed agent decision journaling.
