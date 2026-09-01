CREATE TABLE IF NOT EXISTS component_heartbeats (
    id UUID PRIMARY KEY,
    component VARCHAR(64) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    bot_id UUID,
    sequence_number BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    metadata_json TEXT,
    CONSTRAINT uq_component_instance_bot UNIQUE (component, instance_id, bot_id)
);

CREATE TABLE IF NOT EXISTS bot_runtime_leases (
    bot_id UUID PRIMARY KEY REFERENCES bots(id),
    instance_id VARCHAR(64) NOT NULL,
    lease_id UUID NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS health_events (
    id UUID PRIMARY KEY,
    component VARCHAR(64) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    bot_id UUID,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    detail TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS ops_recovery_runs (
    id UUID PRIMARY KEY,
    bot_id UUID REFERENCES bots(id),
    instance_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_reason TEXT NOT NULL,
    step_details TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS health_events_comp_idx ON health_events (component, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS ops_recovery_bot_idx ON ops_recovery_runs (bot_id, started_at DESC);
