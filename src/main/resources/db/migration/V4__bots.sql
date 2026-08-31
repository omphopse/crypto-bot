CREATE TABLE bots (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  strategy_version_id UUID NOT NULL REFERENCES strategy_versions(id),
  broker VARCHAR(32) NOT NULL CHECK (broker IN ('ALPACA_PAPER', 'BYBIT_DEMO')),
  execution_mode VARCHAR(8) NOT NULL CHECK (execution_mode IN ('PAPER', 'DEMO')),
  status VARCHAR(32) NOT NULL CHECK (status IN ('RUNNING', 'PAUSED', 'STOPPED', 'EMERGENCY_STOPPED')),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX bots_status_idx ON bots (status, created_at DESC);
