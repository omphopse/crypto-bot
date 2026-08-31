# Changelog

## 0.9.0 — 2026-08-31

- Added Micrometer `TradingMetrics` component registering trading operational metrics for submitted orders, executed orders, approved/rejected risk decisions, active reconciliation mismatches, and rebalance runs.
- Configured Spring Boot Actuator health probe groups (`/actuator/health/liveness` and `/actuator/health/readiness`).
- Added multi-stage hardened `Dockerfile` with build isolation, G1GC tuning, and unprivileged user execution (`USER 10001:10001`).
- Updated `docker-compose.yml` with production multi-service topology (PostgreSQL 16, Redis 7, and `algopilot-api`) with healthcheck dependencies.
- Added `.env.example` documenting database, Redis, and broker configuration.
- Added comprehensive operational runbook in `docs/RUNBOOK.md` detailing incident response procedures for reconciliation mismatches, emergency stop resets, stream disconnects, and database rollback safety.
- Added 1 unit test for `TradingMetrics` (119 total passing tests).

## 0.8.0 — 2026-08-31

- Added portfolio allocation drift monitoring engine (`PortfolioRebalanceService.evaluateDrift`) calculating asset weight divergence ($|w_{\text{actual}} - w_{\text{target}}|$) and synthesizing rebalancing order intents.
- Added automated rebalancing execution engine (`PortfolioRebalanceService.executeRebalance`) enforcing mandatory deterministic risk gate evaluation (`RiskDecisionService`) and broker dispatching (`ExecutionGateway`).
- Added Flyway migration `V12__portfolio_rebalancing.sql` and PostgreSQL persistence (`JdbcRebalanceStore`) for `rebalance_runs` and `rebalance_orders`.
- Added REST APIs under `/api/portfolio/rebalance` (`/evaluate-drift`, `/execute`, `/runs`, `/runs/{id}`, `/runs/{id}/orders`).
- Added 4 unit and integration tests covering drift calculations, execution with risk approval, rejected order handling, and REST controllers (118 total passing tests).

## 0.7.0 — 2026-08-31

- Added quantitative multi-asset covariance and Pearson correlation matrix engine (`CorrelationMatrixCalculator`).
- Added deterministic inverse-volatility and risk-parity capital allocation engine (`RiskParityAllocator`) enforcing sum-to-1.0000 invariant.
- Added parametric Value at Risk (VaR 95%) and Expected Shortfall (CVaR 95%) quantitative risk analytics (`PortfolioRiskCalculator`).
- Added application service (`PortfolioAllocationService`) computing portfolio allocation plans, rebalancing deltas, and audit records.
- Added Flyway migration `V11__portfolio_allocation.sql` and PostgreSQL persistence (`JdbcPortfolioAllocationStore`) for `portfolio_allocation_plans`.
- Added REST APIs under `/api/portfolio/allocation` (`/allocate`, `/latest`, `/{id}`, `/history`).
- Added 7 unit and integration tests covering correlation matrix math, risk-parity allocation, portfolio VaR/CVaR, service orchestration, and REST endpoints (114 total passing tests).

## 0.6.0 — 2026-08-31

- Added quantitative multi-factor modeling engine (`FactorEngine`) computing standardized factor scores for Momentum (EMA divergence), Mean Reversion (RSI extremes), Volatility Breakout (ATR expansion), Volume Imbalance (volume spikes), and Trend Strength, with composite alpha weighting.
- Added structured `AlphaHypothesis` generation capturing composite scores, factor attributions, and transparent quantitative rationales.
- Added autonomous strategy synthesis engine (`ResearchAgentService`) formulating versioned strategy JSON definitions parameterized to dominant market factor drivers.
- Implemented automated qualification gating: candidate strategies are rigorously validated against `BacktestEngine` and `WalkForwardEngine` before receiving `APPROVED_CANDIDATE` status.
- Added Flyway migration `V10__research_factors.sql` and PostgreSQL persistence (`JdbcResearchStore`) for `alpha_hypotheses` and `strategy_candidates`.
- Added REST APIs under `/api/research` (`/evaluate-factors`, `/synthesize-strategy`, `/hypotheses`, `/candidates`).
- Added 6 unit and integration tests covering factor calculations, hypothesis creation, strategy synthesis, backtesting validation gating, and controller endpoints (107 total passing tests).

## 0.5.0 — 2026-08-31

- Added high-throughput, thread-safe in-memory pub/sub `MarketEventBus` coordinating market ticks, system events, and order updates without blocking execution threads.
- Added Spring STOMP/WebSocket configuration (`WebSocketConfig`) exposing endpoint `/ws` with SockJS fallback, simple broker `/topic`, and channel interceptor enforcing read-only subscriptions.
- Added `WebSocketEventPublisher` broadcasting market ticks and system events to `/topic/market-data`, `/topic/bot-status`, `/topic/orders`, `/topic/positions`, `/topic/agent-activity`, `/topic/alerts`, and `/topic/reconciliation`.
- Added `AlpacaPaperMarketFeed` streaming processor normalizing trades and quotes from Alpaca Paper streams to canonical `MarketTick` models with live-mode rejection.
- Added `BybitDemoMarketFeed` streaming processor normalizing Bybit V5 Demo ticker/trade packets to canonical `MarketTick` models with demo-only endpoint enforcement.
- Added REST APIs under `/api/feed` (`/status`, `/subscribe`, `/publish`).
- Integrated dynamic WebSocket streaming connection in the operations console (`app.js`) with automatic backoff reconnection.
- Added 12 unit and integration tests covering event bus pub/sub, WebSocket broadcast dispatching, feed normalization, and controller endpoints (101 total passing tests).

## 0.4.0 — 2026-08-31

- Added deterministic event-driven `BacktestEngine` simulating historical OHLCV bar replay with indicator updates, signal generation, and realistic slippage and broker fee deduction.
- Added quantitative technical indicators (`Indicators`) for Simple Moving Average (SMA), Exponential Moving Average (EMA), Relative Strength Index (RSI), and Average True Range (ATR).
- Added `PerformanceMetricsCalculator` computing Max Drawdown %, Sharpe Ratio, Sortino Ratio, Profit Factor, Win Rate %, and marked-to-market equity curves using exact `BigDecimal` math.
- Added `WalkForwardEngine` conducting rolling In-Sample (optimization) and Out-Of-Sample (validation) window analysis to calculate Walk-Forward Efficiency (WFE) and prevent curve fitting.
- Added Flyway migration `V9__backtesting.sql` and PostgreSQL persistence (`JdbcBacktestStore`) for storing backtest runs, trade histories, and walk-forward evaluations.
- Added REST APIs under `/api/backtests` (`/run`, `/{id}`, `/walk-forward`, `/walk-forward/{id}`).
- Added 14 unit and integration tests covering indicator math, performance metrics, backtesting replay, walk-forward efficiency, and controller endpoints (89 total passing tests).

## 0.3.0 — 2026-08-31

- Added non-bypassable `ExecutionGateway` that validates bot execution state, enforces `LIVE_TRADING_DISABLED`, dispatches orders to registered broker adapters, updates order lifecycle states, and records append-only audit events.
- Added `AlpacaPaperAdapter` implementing `BrokerOrderAdapter` and `BrokerStateProvider` for Alpaca Paper Trading (`https://paper-api.alpaca.markets/v2`).
- Added `BybitDemoAdapter` implementing `BrokerOrderAdapter` and `BrokerStateProvider` with HMAC-SHA256 request signing for Bybit Demo Trading (`https://api-demo.bybit.com`).
- Added `CompositeBrokerStateProvider` delegating reconciliation queries to active broker adapters.
- Added operational endpoints under `/api/execution` (`/dispatch/{orderId}`, `/cancel/{orderId}`, `/adapters`).
- Added strict credential isolation ensuring AI and browser components cannot access API keys or secrets.
- Added automated test suite covering order dispatching, cancellation, payload normalization, HMAC signing, and live-trading rejection (75 total passing tests).

## 0.2.0 — 2026-08-31

- Added modular broker-state abstraction (`BrokerStateProvider`) and canonical models (`BrokerAccountBalance`, `BrokerOrder`, `BrokerFill`, `BrokerPosition`, `BrokerStateSnapshot`).
- Added deterministic side-effect-free `ReconciliationEngine` comparing local ledger state against broker state across balances, open orders, fills, and positions.
- Added deterministic mismatch classification with typed categories (`BALANCE_MISMATCH`, `ORDER_MISMATCH`, `FILL_MISMATCH`, `POSITION_MISMATCH`), subcategories, and severity scoring (`CRITICAL`, `WARNING`, `INFO`).
- Added Flyway migration `V8__reconciliation.sql` and PostgreSQL persistence (`JdbcReconciliationStore`) for append-only reconciliation runs, mismatch history, and recovery records.
- Added automated bot safety response: critical discrepancies pause affected running bots, block new order creation, and record critical audit logs.
- Added explicit multi-step recovery workflow (`ReconciliationRecoveryService`) requiring validated matching state before a paused bot can resume.
- Added emergency-stop invariants ensuring emergency-stopped bots cannot resume or recover through ordinary reconciliation workflows.
- Added REST APIs under `/api/reconciliation` for runs, run details, unresolved mismatches, bot status, manual execution, and recovery.
- Added Spring Boot Actuator health indicator (`ReconciliationHealthIndicator`) distinguishing active critical discrepancies from historical resolved events.
- Integrated State Reconciliation & Recovery view and interactive controls into the operations console frontend.
- Added comprehensive unit and integration test suite covering all 16 required reconciliation and recovery scenarios.

## 0.1.0 — 2026-08-31

- Added the ALGOPILOT paper-mode operations console.
- Added Spring Boot API foundation with actuator health checks and Flyway/PostgreSQL configuration.
- Added a typed deterministic risk gate covering order duplication, stop state, data freshness, exposure, daily loss, drawdown, execution quality, and trade-frequency limits.
- Added risk-engine tests and container deployment definitions.
- Added idempotent internal order creation and append-only audit events; rejected decisions cannot create orders.
- Added immutable strategy version creation with audit events and a strategy lifecycle schema.
- Added paper/demo-only bot deployment with persisted lifecycle state and live-trading rejection at the API boundary.
- Added typed agent decision journaling with evidence classification and no direct execution capability.
- Persisted every risk evaluation with its typed request snapshot, deterministic reasons, and audit event.
- Added an audited order lifecycle state machine that rejects invalid and terminal-state transitions.
- Persisted append-only order events alongside global lifecycle audit events.
- Added idempotent fill ingestion and position accounting for average entry and realized P&L.
- Added persisted bot pause, stop, resume, and emergency-stop controls with an emergency-resume guard.
