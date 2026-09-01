CREATE TABLE IF NOT EXISTS strategy_experiments (
    id UUID PRIMARY KEY,
    strategy_id UUID NOT NULL,
    strategy_version_id UUID NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    initial_capital NUMERIC(16, 4) NOT NULL,
    slippage_model VARCHAR(32) NOT NULL,
    slippage_bps NUMERIC(8, 4) NOT NULL,
    maker_fee_bps NUMERIC(8, 4) NOT NULL,
    taker_fee_bps NUMERIC(8, 4) NOT NULL,
    fixed_spread NUMERIC(12, 6) NOT NULL,
    simulated_latency_ms BIGINT NOT NULL,
    market_data_source VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_metrics (
    experiment_id UUID PRIMARY KEY REFERENCES strategy_experiments(id),
    gross_pnl NUMERIC(16, 4) NOT NULL,
    net_pnl NUMERIC(16, 4) NOT NULL,
    total_return_pct NUMERIC(10, 4) NOT NULL,
    cagr NUMERIC(10, 4),
    win_rate_pct NUMERIC(8, 4) NOT NULL,
    avg_win NUMERIC(16, 4) NOT NULL,
    avg_loss NUMERIC(16, 4) NOT NULL,
    profit_factor NUMERIC(10, 4) NOT NULL,
    gross_expectancy NUMERIC(12, 6) NOT NULL,
    net_expectancy NUMERIC(12, 6) NOT NULL,
    max_drawdown_pct NUMERIC(8, 4) NOT NULL,
    sharpe_ratio NUMERIC(8, 4) NOT NULL,
    sortino_ratio NUMERIC(8, 4),
    calmar_ratio NUMERIC(8, 4),
    recovery_factor NUMERIC(8, 4),
    total_trades INT NOT NULL,
    avg_holding_time_ms BIGINT NOT NULL,
    total_fees NUMERIC(16, 4) NOT NULL,
    total_slippage NUMERIC(16, 4) NOT NULL,
    total_spread_cost NUMERIC(16, 4) NOT NULL,
    total_ai_cost NUMERIC(16, 4) NOT NULL DEFAULT 0,
    economic_net_result NUMERIC(16, 4) NOT NULL,
    robustness_score NUMERIC(6, 2) NOT NULL,
    warnings_json TEXT
);

CREATE TABLE IF NOT EXISTS experiment_trades (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES strategy_experiments(id),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    entry_time TIMESTAMPTZ NOT NULL,
    exit_time TIMESTAMPTZ NOT NULL,
    entry_price NUMERIC(16, 6) NOT NULL,
    exit_price NUMERIC(16, 6) NOT NULL,
    quantity NUMERIC(16, 6) NOT NULL,
    gross_pnl NUMERIC(16, 4) NOT NULL,
    net_pnl NUMERIC(16, 4) NOT NULL,
    fee NUMERIC(16, 4) NOT NULL,
    slippage NUMERIC(16, 4) NOT NULL,
    spread_cost NUMERIC(16, 4) NOT NULL,
    exit_reason VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_walk_forward_windows (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES strategy_experiments(id),
    window_index INT NOT NULL,
    in_sample_start TIMESTAMPTZ NOT NULL,
    in_sample_end TIMESTAMPTZ NOT NULL,
    out_of_sample_start TIMESTAMPTZ NOT NULL,
    out_of_sample_end TIMESTAMPTZ NOT NULL,
    in_sample_net_return_pct NUMERIC(10, 4) NOT NULL,
    out_of_sample_net_return_pct NUMERIC(10, 4) NOT NULL,
    oos_degradation_ratio NUMERIC(8, 4) NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_parameter_sweeps (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES strategy_experiments(id),
    parameter_name VARCHAR(64) NOT NULL,
    parameter_value VARCHAR(64) NOT NULL,
    net_return_pct NUMERIC(10, 4) NOT NULL,
    net_expectancy NUMERIC(12, 6) NOT NULL,
    profit_factor NUMERIC(10, 4) NOT NULL,
    max_drawdown_pct NUMERIC(8, 4) NOT NULL,
    robustness_classification VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS experiment_regime_results (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES strategy_experiments(id),
    regime VARCHAR(32) NOT NULL,
    trade_count INT NOT NULL,
    net_pnl NUMERIC(16, 4) NOT NULL,
    win_rate_pct NUMERIC(8, 4) NOT NULL,
    profit_factor NUMERIC(10, 4) NOT NULL,
    net_expectancy NUMERIC(12, 6) NOT NULL,
    max_drawdown_pct NUMERIC(8, 4) NOT NULL
);

CREATE TABLE IF NOT EXISTS strategy_health_metrics (
    id UUID PRIMARY KEY,
    strategy_id UUID NOT NULL,
    backtest_expectancy NUMERIC(12, 6) NOT NULL,
    paper_expectancy NUMERIC(12, 6) NOT NULL,
    demo_expectancy NUMERIC(12, 6) NOT NULL,
    drift_ratio NUMERIC(8, 4) NOT NULL,
    health_status VARCHAR(32) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS exp_strat_idx ON strategy_experiments (strategy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS exp_trades_idx ON experiment_trades (experiment_id, entry_time ASC);
