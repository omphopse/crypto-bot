CREATE TABLE agent_decisions (
  id UUID PRIMARY KEY,
  bot_id UUID NOT NULL REFERENCES bots(id),
  strategy_version_id UUID NOT NULL REFERENCES strategy_versions(id),
  action VARCHAR(32) NOT NULL CHECK (action IN ('BUY', 'SELL', 'HOLD', 'CLOSE', 'REDUCE', 'MOVE_STOP', 'TAKE_PROFIT')),
  symbol VARCHAR(64),
  payload JSONB NOT NULL,
  decided_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX agent_decisions_bot_time_idx ON agent_decisions (bot_id, decided_at DESC);
