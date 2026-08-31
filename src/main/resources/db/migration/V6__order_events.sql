CREATE TABLE order_events (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id),
  previous_status VARCHAR(32) NOT NULL,
  next_status VARCHAR(32) NOT NULL,
  exchange_order_id VARCHAR(128),
  detail VARCHAR(1000),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX order_events_order_time_idx ON order_events (order_id, occurred_at DESC);
