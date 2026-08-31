CREATE TABLE IF NOT EXISTS rebalance_runs (
    id UUID PRIMARY KEY,
    plan_id UUID REFERENCES portfolio_allocation_plans(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    max_drift_pct NUMERIC(10, 4) NOT NULL,
    orders_count INT NOT NULL,
    executed_count INT NOT NULL,
    failed_count INT NOT NULL,
    details TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_rebalance_runs_plan ON rebalance_runs (plan_id);
CREATE INDEX IF NOT EXISTS idx_rebalance_runs_started ON rebalance_runs (started_at DESC);

CREATE TABLE IF NOT EXISTS rebalance_orders (
    id UUID PRIMARY KEY,
    rebalance_run_id UUID REFERENCES rebalance_runs(id) ON DELETE CASCADE,
    bot_id UUID REFERENCES bots(id) ON DELETE CASCADE,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    price NUMERIC(18, 4) NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rebalance_orders_run ON rebalance_orders (rebalance_run_id);
CREATE INDEX IF NOT EXISTS idx_rebalance_orders_bot ON rebalance_orders (bot_id);
