CREATE TABLE orders (
  id UUID PRIMARY KEY,
  client_order_id VARCHAR(128) NOT NULL UNIQUE,
  bot_id VARCHAR(128) NOT NULL,
  strategy_version_id VARCHAR(128) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
  quantity NUMERIC(30, 12) NOT NULL CHECK (quantity > 0),
  reference_price NUMERIC(30, 12) NOT NULL CHECK (reference_price > 0),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX orders_bot_status_time_idx ON orders (bot_id, status, created_at DESC);
