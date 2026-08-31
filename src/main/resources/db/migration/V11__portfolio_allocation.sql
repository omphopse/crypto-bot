CREATE TABLE IF NOT EXISTS portfolio_allocation_plans (
    id UUID PRIMARY KEY,
    total_capital NUMERIC(18, 4) NOT NULL,
    portfolio_volatility NUMERIC(10, 4) NOT NULL,
    value_at_risk_95 NUMERIC(10, 4) NOT NULL,
    expected_shortfall_95 NUMERIC(10, 4) NOT NULL,
    weights_json JSONB NOT NULL,
    rationale TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolio_allocation_created ON portfolio_allocation_plans (created_at DESC);
