CREATE TABLE IF NOT EXISTS position_lifecycle_records (
    position_id UUID PRIMARY KEY REFERENCES positions(id),
    bot_id UUID REFERENCES bots(id),
    strategy_version_id UUID NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    initial_quantity NUMERIC(30,12) NOT NULL,
    current_quantity NUMERIC(30,12) NOT NULL,
    entry_price NUMERIC(30,12) NOT NULL,
    initial_stop_loss NUMERIC(30,12),
    current_stop_loss NUMERIC(30,12),
    take_profit NUMERIC(30,12),
    trailing_stop_pct NUMERIC(10,4),
    high_water_mark NUMERIC(30,12),
    state VARCHAR(32) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS position_snapshots (
    id UUID PRIMARY KEY,
    position_id UUID REFERENCES positions(id),
    bot_id UUID REFERENCES bots(id),
    symbol VARCHAR(64) NOT NULL,
    quantity NUMERIC(30,12) NOT NULL,
    entry_price NUMERIC(30,12) NOT NULL,
    market_price NUMERIC(30,12) NOT NULL,
    market_value NUMERIC(30,12) NOT NULL,
    unrealized_pnl NUMERIC(30,12) NOT NULL,
    realized_pnl NUMERIC(30,12) NOT NULL,
    stop_loss NUMERIC(30,12),
    take_profit NUMERIC(30,12),
    trailing_stop NUMERIC(30,12),
    mfe NUMERIC(30,12),
    mae NUMERIC(30,12),
    holding_time_ms BIGINT NOT NULL,
    snapshot_timestamp TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS position_stop_history (
    id UUID PRIMARY KEY,
    position_id UUID REFERENCES positions(id),
    bot_id UUID REFERENCES bots(id),
    previous_stop NUMERIC(30,12),
    new_stop NUMERIC(30,12) NOT NULL,
    reason TEXT NOT NULL,
    decision_id UUID,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS position_exit_events (
    id UUID PRIMARY KEY,
    position_id UUID REFERENCES positions(id),
    bot_id UUID REFERENCES bots(id),
    event_type VARCHAR(64) NOT NULL,
    exit_reason VARCHAR(64),
    quantity NUMERIC(30,12) NOT NULL,
    price NUMERIC(30,12) NOT NULL,
    realized_pnl NUMERIC(30,12),
    order_id UUID,
    event_timestamp TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS pos_snapshots_pos_idx ON position_snapshots (position_id, snapshot_timestamp DESC);
CREATE INDEX IF NOT EXISTS pos_stop_history_pos_idx ON position_stop_history (position_id, modified_at DESC);
CREATE INDEX IF NOT EXISTS pos_exit_events_pos_idx ON position_exit_events (position_id, event_timestamp DESC);
