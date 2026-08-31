CREATE TABLE strategies (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT', 'PAPER', 'DEMO', 'DEPLOYED', 'PAUSED', 'RETIRED')),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE strategy_versions (
  id UUID PRIMARY KEY,
  strategy_id UUID NOT NULL REFERENCES strategies(id),
  version_number INTEGER NOT NULL CHECK (version_number > 0),
  definition JSONB NOT NULL,
  change_reason VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT strategy_versions_strategy_number_unique UNIQUE (strategy_id, version_number)
);
CREATE INDEX strategy_versions_strategy_time_idx ON strategy_versions (strategy_id, created_at DESC);
