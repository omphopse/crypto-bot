CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actor_type VARCHAR(32) NOT NULL,
  actor_id VARCHAR(128),
  event_type VARCHAR(96) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX audit_events_aggregate_time_idx ON audit_events (aggregate_type, aggregate_id, occurred_at DESC);
CREATE TABLE risk_decisions (
  id UUID PRIMARY KEY,
  client_order_id VARCHAR(128) NOT NULL UNIQUE,
  bot_id VARCHAR(128) NOT NULL,
  strategy_version_id VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('APPROVED','REJECTED')),
  reasons JSONB NOT NULL,
  evaluated_at TIMESTAMPTZ NOT NULL,
  request_snapshot JSONB NOT NULL
);
CREATE INDEX risk_decisions_bot_time_idx ON risk_decisions (bot_id, evaluated_at DESC);
