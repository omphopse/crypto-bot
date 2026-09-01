CREATE TABLE IF NOT EXISTS discovery_candidates (
    candidate_id UUID PRIMARY KEY,
    fingerprint VARCHAR(64) UNIQUE NOT NULL,
    base_strategy_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    family VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    parameters_json TEXT NOT NULL,
    generation_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    robustness_classification VARCHAR(32),
    robustness_score NUMERIC(6, 2),
    net_expectancy NUMERIC(12, 6),
    profit_factor NUMERIC(10, 4),
    max_drawdown_pct NUMERIC(8, 4),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS candidate_stress_results (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES discovery_candidates(candidate_id),
    stress_type VARCHAR(32) NOT NULL,
    stress_multiplier NUMERIC(6, 2) NOT NULL,
    simulated_net_pnl NUMERIC(16, 4) NOT NULL,
    simulated_net_expectancy NUMERIC(12, 6) NOT NULL,
    simulated_profit_factor NUMERIC(10, 4) NOT NULL,
    is_profitable BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS candidate_paper_validations (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES discovery_candidates(candidate_id),
    backtest_expectancy NUMERIC(12, 6) NOT NULL,
    paper_expectancy NUMERIC(12, 6) NOT NULL,
    fill_rate_pct NUMERIC(8, 4) NOT NULL,
    actual_slippage_bps NUMERIC(8, 4) NOT NULL,
    actual_fee_bps NUMERIC(8, 4) NOT NULL,
    drift_detected BOOLEAN NOT NULL,
    drift_status VARCHAR(32) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS cand_family_idx ON discovery_candidates (family, status);
CREATE INDEX IF NOT EXISTS cand_rank_idx ON discovery_candidates (net_expectancy DESC NULLS LAST, robustness_score DESC NULLS LAST);
