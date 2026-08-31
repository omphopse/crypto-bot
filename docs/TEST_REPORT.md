# Test report

## 2026-08-31 — Real-Time WebSocket Streaming & Market Data Feed Milestone

Command: `mvn test -q`

Result: passed (101 tests executed across 32 test classes, 0 failures, 0 errors, 0 skipped).

### Covered WebSocket Streaming & Market Data Feed Scenarios:

1. **Market Event Bus (`MarketEventBus`):**
   - Global tick distribution to active consumers.
   - Symbol-filtered tick distribution (e.g. `BTC/USD`, `ETH/USD`).
   - System event pub/sub across topics (`orders`, `bots`, `positions`, `reconciliation`).
   - Thread-safe subscriber registration and cleanup.

2. **WebSocket Event Broadcasting (`WebSocketEventPublisher`):**
   - Forwarding market ticks to `/topic/market-data` and `/topic/market-data/{symbol}`.
   - Forwarding system lifecycle events to `/topic/{topic}` destinations.
   - Exception handling on broadcast channels.

3. **Alpaca Paper Market Feed (`AlpacaPaperMarketFeed`):**
   - Trade packet (`t`) normalization into `MarketTick` models with timestamp parsing.
   - Quote packet (`q`) normalization into `MarketTick` models with mid-price calculation.
   - Lifecycle management (start, stop, subscribe, unsubscribe).
   - Live-trading URL rejection.

4. **Bybit Demo Market Feed (`BybitDemoMarketFeed`):**
   - Ticker and trade message normalization into `MarketTick` models with timestamp extraction.
   - Symbol normalization and precision scaling (scale 4).
   - Lifecycle management and demo-mode URL verification.

5. **Feed REST Controller (`FeedController`):**
   - Status retrieval (`/api/feed/status`).
   - Dynamic symbol subscription (`/api/feed/subscribe`).
   - Test tick ingestion and publication (`/api/feed/publish`).

### Earlier Verified Milestone Suites (All Passing):
- Event-Driven Backtesting & Walk-Forward Validation Engine (14 tests).
- Exchange Adapters (Alpaca Paper & Bybit Demo) and Execution Gateway (21 tests).
- Reconciliation & Recovery engine, service, controller, and health indicators (54 tests).
- Deterministic Risk Engine evaluation.
- Idempotent order store and lifecycle state machine.
- Fill ingestion and position accounting.
- Bot operational controls and emergency stop guard.
- Strategy immutability and versioning.
- Typed agent decision journaling.
