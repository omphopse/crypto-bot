# Clean Reset Inventory & Data State Specification

## 1. Overview
The Clean Reset mechanism provides a controlled, transactionally safe method to clear all experimental, persisted trading, decision, and runtime state while strictly preserving Flyway schema history, database tables, indexes, security policies, and system configuration.

---

## 2. Cleared vs. Preserved Data Inventory

### Cleared Tables (Experimental & Runtime State)
| Category | Tables Cleared |
| :--- | :--- |
| **Trading & Orders** | `orders`, `order_events`, `fills`, `positions`, `position_lifecycle_records`, `position_snapshots`, `position_stop_history`, `position_exit_events` |
| **Portfolio & P&L** | `portfolio_snapshots`, `pnl_records`, `portfolio_allocations`, `allocation_targets`, `portfolio_rebalance_runs`, `rebalance_orders` |
| **Agent & Decisions** | `agent_sessions`, `agent_state_events`, `agent_decisions`, `structured_trade_decisions`, `trading_contexts`, `validated_trade_intents`, `strategy_validation_results`, `autonomous_execution_results` |
| **Research & Market Data** | `market_observations`, `market_scan_results`, `research_requests`, `research_sources`, `research_documents`, `research_evidence`, `alpha_factors`, `factor_evaluations` |
| **Operations & Heartbeats** | `component_heartbeats`, `bot_runtime_leases`, `health_events`, `ops_recovery_runs`, `reconciliations`, `reconciliation_mismatches` |
| **Strategy & Experiments** | `strategies`, `strategy_versions`, `bots`, `backtests`, `backtest_trades`, `strategy_experiments`, `experiment_metrics`, `experiment_trades`, `experiment_walk_forward_windows`, `experiment_parameter_sweeps`, `experiment_regime_results`, `strategy_health_metrics`, `strategy_candidates`, `candidate_stress_results`, `candidate_paper_validations` |
| **AI Governance & Audit** | `ai_cost_events`, `risk_rejections`, `audit_events` |

---

### Preserved Tables & Configurations (System Foundation)
| Category | Tables / Stores Preserved |
| :--- | :--- |
| **Database Schema** | `flyway_schema_history` (All migrations V1–V25 intact) |
| **Reset Audit Ledger** | `reset_operations` (Immutable audit log of reset timestamps and operators) |
| **AI Cost Pricing** | `ai_model_pricing` (Base token pricing catalogs) |
| **AI Budget Policies** | `ai_budget_policies` (Default budget and threshold rules) |
| **Risk Limit Definitions**| `risk_limits` (System default global safety caps) |
| **System Configuration** | Spring Boot properties, application YAML, logging configurations |
