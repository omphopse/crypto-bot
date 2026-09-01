CREATE TABLE IF NOT EXISTS market_observations (
    id UUID PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    last_price NUMERIC(30,12) NOT NULL,
    bid NUMERIC(30,12),
    ask NUMERIC(30,12),
    spread NUMERIC(30,12),
    volume NUMERIC(30,12) NOT NULL,
    open_price NUMERIC(30,12),
    high_price NUMERIC(30,12),
    low_price NUMERIC(30,12),
    close_price NUMERIC(30,12),
    timeframe VARCHAR(16) NOT NULL,
    market_timestamp TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    is_stale BOOLEAN NOT NULL DEFAULT FALSE,
    freshness_ms BIGINT NOT NULL,
    validation_status VARCHAR(32) NOT NULL
);

CREATE INDEX IF NOT EXISTS market_observations_symbol_time_idx ON market_observations (symbol, market_timestamp DESC);
CREATE INDEX IF NOT EXISTS market_observations_provider_idx ON market_observations (provider, environment);

CREATE TABLE IF NOT EXISTS market_scan_results (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES agent_sessions(id),
    bot_id UUID REFERENCES bots(id),
    symbol VARCHAR(64) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    candidate_type VARCHAR(64) NOT NULL,
    trigger_conditions JSONB NOT NULL DEFAULT '[]',
    indicator_snapshot JSONB NOT NULL DEFAULT '{}',
    market_snapshot JSONB NOT NULL DEFAULT '{}',
    confidence_score NUMERIC(8,4),
    reason VARCHAR(512),
    status VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS market_scan_results_time_idx ON market_scan_results (timestamp DESC);
CREATE INDEX IF NOT EXISTS market_scan_results_symbol_idx ON market_scan_results (symbol, timestamp DESC);
