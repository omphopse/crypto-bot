CREATE TABLE IF NOT EXISTS structured_trade_decisions (
    id UUID PRIMARY KEY,
    context_id UUID REFERENCES trading_contexts(id),
    context_hash VARCHAR(64) NOT NULL,
    bot_id UUID REFERENCES bots(id),
    session_id UUID REFERENCES agent_sessions(id),
    strategy_version_id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    confidence NUMERIC(10,4) NOT NULL,
    quantity NUMERIC(30,12),
    reference_price NUMERIC(30,12),
    stop_loss NUMERIC(30,12),
    take_profit NUMERIC(30,12),
    time_horizon VARCHAR(32),
    thesis TEXT NOT NULL,
    evidence_references JSONB NOT NULL DEFAULT '[]',
    risk_factors JSONB NOT NULL DEFAULT '[]',
    invalidation_conditions JSONB NOT NULL DEFAULT '[]',
    validation_status VARCHAR(32) NOT NULL,
    rejection_reason TEXT,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(10,6) NOT NULL DEFAULT 0,
    decision_timestamp TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS structured_decisions_bot_idx ON structured_trade_decisions (bot_id, decision_timestamp DESC);
CREATE INDEX IF NOT EXISTS structured_decisions_hash_idx ON structured_trade_decisions (context_hash);
