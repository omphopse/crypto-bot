CREATE TABLE IF NOT EXISTS agent_sessions (
    id UUID PRIMARY KEY,
    bot_id UUID NOT NULL REFERENCES bots(id),
    name VARCHAR(128) NOT NULL,
    current_state VARCHAR(32) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    stopped_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS agent_sessions_bot_idx ON agent_sessions (bot_id, started_at DESC);

CREATE TABLE IF NOT EXISTS agent_state_events (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES agent_sessions(id),
    bot_id UUID NOT NULL REFERENCES bots(id),
    from_state VARCHAR(32) NOT NULL,
    to_state VARCHAR(32) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    timestamp TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS agent_state_events_session_idx ON agent_state_events (session_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS agent_state_events_bot_idx ON agent_state_events (bot_id, timestamp DESC);
