CREATE TABLE IF NOT EXISTS portfolio_accounts (
    id VARCHAR(64) PRIMARY KEY,
    starting_capital NUMERIC(30,12) NOT NULL DEFAULT 100000.00,
    cash_balance NUMERIC(30,12) NOT NULL DEFAULT 100000.00,
    reserved_exposure NUMERIC(30,12) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO portfolio_accounts (id, starting_capital, cash_balance, reserved_exposure, updated_at)
VALUES ('GLOBAL', 100000.00, 100000.00, 0.00, NOW())
ON CONFLICT (id) DO NOTHING;
