CREATE TABLE reconciliation_runs (
  id UUID PRIMARY KEY,
  bot_id VARCHAR(128),
  broker VARCHAR(32) NOT NULL,
  execution_mode VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('STARTED', 'MATCHED', 'MISMATCHED', 'FAILED')),
  mismatch_count INTEGER NOT NULL DEFAULT 0,
  error_detail VARCHAR(1000),
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reconciliation_runs_bot_time_idx ON reconciliation_runs (bot_id, created_at DESC);
CREATE INDEX reconciliation_runs_status_idx ON reconciliation_runs (status, created_at DESC);

CREATE TABLE reconciliation_mismatches (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
  bot_id VARCHAR(128),
  category VARCHAR(64) NOT NULL CHECK (category IN ('BALANCE_MISMATCH', 'ORDER_MISMATCH', 'FILL_MISMATCH', 'POSITION_MISMATCH')),
  mismatch_type VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NOT NULL CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO')),
  symbol VARCHAR(64),
  local_value JSONB NOT NULL,
  broker_value JSONB NOT NULL,
  resolution_state VARCHAR(32) NOT NULL CHECK (resolution_state IN ('UNRESOLVED', 'RESOLVED', 'ACKNOWLEDGED')),
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reconciliation_mismatches_run_idx ON reconciliation_mismatches (run_id, created_at DESC);
CREATE INDEX reconciliation_mismatches_bot_state_idx ON reconciliation_mismatches (bot_id, resolution_state, severity);

CREATE TABLE reconciliation_recoveries (
  id UUID PRIMARY KEY,
  bot_id VARCHAR(128) NOT NULL,
  run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
  operator_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('REQUESTED', 'COMPLETED', 'REJECTED')),
  reason VARCHAR(500),
  recovered_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reconciliation_recoveries_bot_time_idx ON reconciliation_recoveries (bot_id, recovered_at DESC);
