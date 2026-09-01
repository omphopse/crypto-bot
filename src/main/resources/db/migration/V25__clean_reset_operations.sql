CREATE TABLE IF NOT EXISTS reset_operations (
    id UUID PRIMARY KEY,
    operator VARCHAR(64) NOT NULL,
    confirmation_string VARCHAR(128) NOT NULL,
    tables_cleared TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL
);
