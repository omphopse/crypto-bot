CREATE TABLE IF NOT EXISTS alpha_hypotheses (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    composite_score NUMERIC(10, 4) NOT NULL,
    factors_json JSONB NOT NULL,
    rationale TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alpha_hypotheses_symbol_time ON alpha_hypotheses (symbol, created_at DESC);

CREATE TABLE IF NOT EXISTS strategy_candidates (
    id UUID PRIMARY KEY,
    hypothesis_id UUID REFERENCES alpha_hypotheses(id) ON DELETE SET NULL,
    strategy_version_id UUID REFERENCES strategy_versions(id) ON DELETE CASCADE,
    backtest_id UUID REFERENCES backtest_runs(id) ON DELETE SET NULL,
    walk_forward_id UUID REFERENCES walk_forward_runs(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_strategy_candidates_version ON strategy_candidates (strategy_version_id);
CREATE INDEX IF NOT EXISTS idx_strategy_candidates_created ON strategy_candidates (created_at DESC);
