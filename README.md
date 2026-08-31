# ALGOPILOT

ALGOPILOT is a safety-first autonomous algorithmic-trading operations platform. This delivery includes a control console plus real backend boundaries: Spring Boot, Flyway/PostgreSQL configuration, actuator health endpoints, typed deterministic risk evaluation, broker state reconciliation, real Paper/Demo broker adapters (Alpaca Paper & Bybit Demo) wired through a non-bypassable Execution Gateway, an event-driven backtesting and walk-forward validation engine, real-time WebSocket market streaming gateway and event bus, and an autonomous multi-factor strategy research and synthesis engine. The console is deliberately locked to **PAPER** execution; live broker trading is strictly disabled.

## Run locally

No dependency installation is required for the control console. Open `index.html` in a browser, or run:

```sh
python3 -m http.server 8080
```

Then open `http://localhost:8080`.

## API development

Run `docker compose up postgres`, then `mvn test` and `mvn spring-boot:run`. The health endpoint is `GET /actuator/health`; the typed risk gate is `POST /api/risk/evaluate`, and `POST /api/orders` creates an idempotent internal order only after risk approval.

- `POST /api/bots`: Deploys a persisted bot against an immutable strategy version. Only `ALPACA_PAPER`/`PAPER` and `BYBIT_DEMO`/`DEMO` pairs are accepted; live trading is rejected.
- `POST /api/agent/decisions`: Journals typed agent intent and evidence. It does not execute; an order must separately pass the risk and order boundaries.
- `POST /api/execution/dispatch/{orderId}`: Dispatches an approved order through `ExecutionGateway` to the appropriate paper/demo broker adapter (`AlpacaPaperAdapter` or `BybitDemoAdapter`).
- `POST /api/execution/cancel/{orderId}`: Requests order cancellation on the broker adapter.
- `GET /api/execution/adapters`: Lists registered broker adapters and their supported modes.
- `POST /api/research/evaluate-factors`: Evaluates multi-factor market matrix (momentum, mean reversion, volatility breakout, volume spike, trend strength) and produces an `AlphaHypothesis`.
- `POST /api/research/synthesize-strategy`: Synthesizes candidate strategy version from dominant factor drivers, running backtesting and walk-forward validation gates.
- `GET /api/research/hypotheses`: Retrieves recent alpha hypotheses.
- `GET /api/research/candidates`: Retrieves synthesized strategy candidates and qualification statuses.
- `GET /api/feed/status`: Returns active paper/demo streaming feeds, connection states, and subscribed symbols.
- `POST /api/feed/subscribe`: Subscribes a market data feed to a symbol ticker stream.
- `POST /api/feed/publish`: Ingests and broadcasts a market tick through the internal `MarketEventBus`.
- `POST /api/backtests/run`: Runs an event-driven backtest on historical/candle series with realistic slippage and broker fee deduction.
- `GET /api/backtests`: Lists recent backtest execution runs.
- `GET /api/backtests/{id}`: Retrieves complete backtest report, trades, and marked-to-market equity curve.
- `POST /api/backtests/walk-forward`: Executes rolling In-Sample / Out-Of-Sample walk-forward validation to assess strategy robustness.
- `GET /api/backtests/walk-forward/{id}`: Retrieves walk-forward efficiency report and window results.
- `POST /api/reconciliation/run`: Runs deterministic reconciliation comparing local ledger (balance, active orders, fills, positions) against broker state.
- `GET /api/reconciliation/runs`: Lists recent reconciliation runs and results.
- `GET /api/reconciliation/runs/{id}`: Retrieves reconciliation run details and specific mismatch records.
- `GET /api/reconciliation/mismatches`: Lists unresolved discrepancies across bots and assets.
- `GET /api/reconciliation/status/{botId}`: Returns current reconciliation health, active discrepancies, and recovery requirements for a bot.
- `POST /api/reconciliation/recover`: Executes explicit operator recovery for a paused bot once reconciliation state is matched.
- Bot controls: `POST /api/bots/{id}/pause`, `/stop`, `/emergency-stop`, and `/resume`. Emergency-stopped bots reject ordinary resume requests pending dedicated emergency recovery procedures.

## WebSocket Streaming

Clients connect to `ws://localhost:8080/ws` (with SockJS fallback). Broadcast channels:
- `/topic/market-data` & `/topic/market-data/{symbol}`: Real-time price ticks (`MarketTick`).
- `/topic/bot-status`: Real-time bot state transitions.
- `/topic/orders`: Order lifecycle updates (`SUBMITTED`, `ACKNOWLEDGED`, `FILLED`, `CANCELLED`).
- `/topic/positions`: Real-time portfolio position updates.
- `/topic/agent-activity`: Agent decision journal stream.
- `/topic/alerts`: Safety and risk limit alerts.
- `/topic/reconciliation`: Discrepancy detection and recovery events.

WebSocket channels are strictly read-only for connected clients; command injection is rejected by channel interceptors.

## Safety boundary

No UI action is an execution authority. Production order flow must be:

`agent/strategy → typed decision → deterministic risk engine → idempotent execution gateway → broker adapter`

The browser/research layer is an untrusted information source only. Live trading is disabled by design until separately implemented with explicit deployment gates.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the service design, [docs/TRADING_SAFETY.md](docs/TRADING_SAFETY.md) for non-negotiable controls, and [docs/SECURITY.md](docs/SECURITY.md) for security boundaries.

Milestone status and test evidence are recorded in [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) and [docs/TEST_REPORT.md](docs/TEST_REPORT.md).
