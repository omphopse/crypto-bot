# Changelog

## 0.17.0 — 2026-09-01

- Implemented Autonomous Intelligence Layer — Phase E (Structured LLM Decision Engine):
  - Added Flyway migration `V18__structured_trade_decisions.sql` creating `structured_trade_decisions` table.
  - Implemented `LLMDecisionProvider` interface and `FakeLLMDecisionProvider` for deterministic, offline testing.
  - Implemented `StructuredTradeDecision` immutable record with typed action enums (`NO_ACTION`, `BUY`, `SELL`, `HOLD`, `CLOSE`, `REDUCE`).
  - Implemented `DecisionPromptBuilder` structuring prompt sections (`[SYSTEM RULES]`, `[TRUSTED MARKET DATA]`, `[TRUSTED STRATEGY STATE]`, `[TRUSTED PORTFOLIO STATE]`, `[TRUSTED RISK STATE]`, `[TRUSTED RECONCILIATION STATE]`, `[DETERMINISTIC SCANNER RESULTS]`, `[UNTRUSTED EXTERNAL RESEARCH EVIDENCE]`).
  - Implemented `StructuredDecisionValidator` enforcing symbol matching, strategy version matching, stop-loss/take-profit direction sanity, evidence provenance, context hash verification, and safety gate observance.
  - Implemented `AiCostLimiter` tracking requests per minute/day and daily token cost budgets.
  - Implemented `LLMDecisionEngineService` and `JdbcStructuredDecisionStore` with zero order execution capabilities.
  - Added structured decision endpoints and updated web operations console table with `DECISION ONLY — NOT EXECUTED` indicators.
  - Added `docs/DECISION_ENGINE.md`.
  - Added comprehensive automated unit tests in `StructuredDecisionValidatorTest` and `LLMDecisionEngineServiceTest` (174 total passing tests).

## 0.16.0 — 2026-09-01

- Implemented Autonomous Intelligence Layer — Phase D (Typed, Prompt-Safe Context Builder):
  - Added Flyway migration `V17__trading_contexts.sql` creating `trading_contexts` table for persistent context snapshots and SHA-256 fingerprinting.
  - Implemented strongly typed context domain records (`MarketContext`, `IndicatorContext`, `ScannerContext`, `StrategyContext`, `PortfolioContext`, `PositionContext`, `OpenOrderContext`, `RiskContext`, `ReconciliationContext`, `ResearchEvidenceContext`, `PerformanceContext`, `FreshnessSummary`, `SafetySummary`, `TradingContext`).
  - Implemented `ContextBuilderService` aggregating verified market data, indicator snapshots, scanner candidates, immutable strategy parameters, authoritative portfolio accounting, positions, orders, risk limits, reconciliation state, and isolated untrusted research evidence.
  - Implemented deterministic SHA-256 `contextHash` over time buckets and portfolio marks.
  - Implemented deterministic safety gates (`marketDataValid`, `marketDataFresh`, `riskStateValid`, `reconciliationHealthy`, `strategyActive`, `executionAllowed`).
  - Implemented `JdbcTradingContextStore` and `ContextController` exposing `/api/context/{botId}` REST endpoints.
  - Added Live Trading Context Snapshot panel to the web operations console (`index.html` & `app.js`).
  - Added `docs/CONTEXT_MODEL.md`.
  - Added comprehensive automated unit tests in `ContextBuilderServiceTest` (166 total passing tests).

## 0.15.0 — 2026-09-01

- Implemented Autonomous Intelligence Layer — Phase C (Research Service and Secure Browser Abstraction):
  - Added Flyway migration `V16__research_service_and_evidence.sql` creating `research_requests`, `research_sources`, `research_documents`, and `research_evidence`.
  - Implemented `DomainSecurityValidator` for strict SSRF protection (rejection of non-HTTPS schemes, localhost, private IP subnets, link-local IPs, and metadata service addresses).
  - Implemented `SourcePolicy` supporting allowlist/blocklist configurations for authoritative financial & regulatory domains.
  - Implemented multi-vector `PromptInjectionDetector` detecting instruction-override triggers ("ignore previous instructions", "system message", "reveal credentials") with typed security statuses (`CLEAN`, `SUSPICIOUS`, `BLOCKED`).
  - Implemented `ContentSanitizer` stripping HTML/script/style elements and enforcing maximum document length limits.
  - Implemented `ResearchRateLimiter` preventing runaway research loops.
  - Implemented `ResearchService`, `ResearchStore`, and `JdbcResearchStore` for structured evidence extraction and persistence.
  - Added `ResearchController` REST endpoints under `/api/research`.
  - Added Autonomous Web Research & Evidence panel to web operations console.
  - Added comprehensive security unit tests in `DomainSecurityValidatorTest`, `PromptInjectionDetectorTest`, `ContentSanitizerTest`, and `ResearchServiceTest` (161 total passing tests).
  - Added `docs/AI_SAFETY.md` and `docs/RESEARCH_SECURITY.md`.

## 0.14.0 — 2026-09-01

- Implemented Autonomous Intelligence Layer — Phase B (Market Observation and Scanner):
  - Added Flyway migration `V15__market_observation_and_scanner.sql` creating `market_observations` and `market_scan_results`.
  - Created `MarketObservation` domain model with strict validation rules (non-positive price, negative volume, inverted OHLC, crossed quotes rejection) and freshness tracking.
  - Implemented `MarketObservationService`, `MarketDataStore`, and `JdbcMarketDataStore` for normalized market data ingestion and persistence.
  - Extended `Indicators` with high-precision Bollinger Bands, MACD, and neutral flat-market RSI handling.
  - Implemented deterministic `MarketScanner` producing `ScanResult` records (Momentum, Breakout, Volume Anomaly, Oversold, Overbought, Mean Reversion) with zero trading authority.
  - Created `MarketController` exposing `/api/market/observations` and `/api/market/scans`.
  - Added Market Observations & Scanner Detections panel to web dashboard.
  - Added comprehensive unit tests in `MarketObservationServiceTest` and `MarketScannerTest` (145 total passing tests).

## 0.13.0 — 2026-09-01

- Implemented Autonomous Intelligence Layer — Phase A (Agent State Machine):
  - Added Flyway migration `V14__autonomous_agent_state_machine.sql` creating `agent_sessions` and `agent_state_events`.
  - Defined explicit canonical `AgentState` enum: `IDLE`, `OBSERVING`, `SCANNING`, `RESEARCHING`, `ANALYZING`, `DECIDING`, `RISK_CHECK`, `EXECUTING`, `MONITORING`, `EXIT_EVALUATION`, `PAUSED`, `ERROR`, `STOPPED`.
  - Defined `AutonomousMode` enum: `OFF`, `OBSERVE_ONLY`, `PAPER_AUTONOMOUS`, `DEMO_AUTONOMOUS`, `LIVE_LOCKED`.
  - Implemented `AgentStateMachine` validating legal lifecycle state transitions, managing sessions, and emitting audit events on every transition.
  - Implemented `AgentStateStore` and `JdbcAgentStateStore` for PostgreSQL persistence of session states and transition events.
  - Added `AgentStateController` REST endpoints under `/api/agent/state`.
  - Added unit test suite `AgentStateMachineTest` verifying full autonomous lifecycle transitions, error recovery, pause/resume, and rejection of illegal transitions and `LIVE_LOCKED` mode (134 total passing tests).

## 0.12.0 — 2026-09-01

- Implemented authoritative mark-to-market financial accounting engine (`PortfolioAccountingService`, `PortfolioSummary`, `PositionMark`):
  - Strict mathematical invariant: $\text{Equity} \equiv \text{Cash} + \text{Market Value of Open Positions} \equiv \text{Starting Capital} + \text{Realized P\&L} + \text{Unrealized P\&L} - \text{Cumulative Fees}$.
  - Realized P&L, unrealized P&L, cost basis, market exposure, gross exposure, and broker fees cleanly separated with zero double-counting.
  - REST endpoints at `/api/portfolio/accounting/summary` and `/api/portfolio/accounting/positions-mark`.
- Multi-instance concurrency hardening:
  - Added Flyway migration `V13__portfolio_accounting.sql` with `portfolio_accounts` table.
  - Implemented database-backed row-level locking (`SELECT ... FOR UPDATE`) in `OrderService` for multi-process distributed risk gating.
  - Implemented authoritative pending order reservations consuming portfolio exposure during in-flight states.
- Enhanced web dashboard with distinct metric cards: Paper/Demo Portfolio Value, Cash Balance, P&L Breakdown (Realized, Unrealized, Fees), Cost Basis vs Market Exposure, Risk Utilization %, and Mark-to-Market Positions table.
- Added comprehensive unit and regression tests in `PortfolioAccountingServiceTest` (128 total passing tests).

## 0.11.0 — 2026-09-01

- Published execution incident forensic report `docs/EXECUTION_INCIDENT_2026-09-01.md` analyzing repeated order bursts on Alpaca Paper and exposure breaches.
- Implemented architectural root-cause fix in `OrderService`:
  - Authoritative calculation of settled positions (`PositionStore`) + in-flight pending orders (`OrderStore.findAllOpenOrders()`).
  - Fair atomic concurrency lock (`ReentrantLock`) preventing race conditions during concurrent order requests.
  - Strict enforcement of global portfolio exposure (50% max) and symbol position limits (10% max) including in-flight orders.
  - All automated bots in `DataSeeder.java` set to `PAUSED` by default to prevent unmonitored order loops.
- Added regression tests for symbol pending order breaches, portfolio exposure breaches, and 10-thread concurrent order spam (123 total passing tests).
- Configured GitHub remote `origin` to `https://github.com/omphopse/crypto-bot.git`.

## 0.10.0 — 2026-09-01

- Added complete Data Integrity Audit and published `docs/DATA_INTEGRITY_REPORT.md` classifying all production, seed, test, mock, and simulated execution paths.
- Configured single controlled **Canary validation deployment**: 1 Alpaca Paper bot (`Canary Alpaca Paper`) and 1 Bybit Demo bot (`Canary Bybit Demo`) in `RUNNING` status, leaving other bots in `PAUSED` state by default.
- Added critical reconciliation mismatch order-blocking gate in `ExecutionGateway` to prevent order dispatching if unresolved discrepancies exist.
- Standardized UI dashboard terminology and badges (`PAPER`, `DEMO`, `SIMULATED P&L`, `Simulated Net P&L`, `Paper/Demo Portfolio Equity`) to clearly differentiate paper operations from real fiat currency.
- Added execution provenance tracking (provider, environment, provider order ID, client order ID, timestamps) to trade execution details.
- Added automated unit test verifying order dispatch rejection when critical reconciliation mismatches exist (120 total passing tests).

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
