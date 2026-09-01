CREATE TABLE IF NOT EXISTS trading_contexts (
    id UUID PRIMARY KEY,
    context_hash VARCHAR(64) NOT NULL,
    bot_id UUID REFERENCES bots(id),
    session_id UUID REFERENCES agent_sessions(id),
    symbol VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    agent_state VARCHAR(64) NOT NULL,
    autonomous_mode VARCHAR(64) NOT NULL,
    market_price NUMERIC(30,12),
    portfolio_equity NUMERIC(30,12),
    freshness_status VARCHAR(32) NOT NULL,
    execution_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSONB NOT NULL DEFAULT '{}',
    generated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS trading_contexts_bot_idx ON trading_contexts (bot_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS trading_contexts_hash_idx ON trading_contexts (context_hash);
