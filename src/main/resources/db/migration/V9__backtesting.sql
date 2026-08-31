CREATE TABLE backtest_runs (
  id UUID PRIMARY KEY,
  strategy_version_id UUID NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  timeframe VARCHAR(16) NOT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL,
  initial_capital NUMERIC(19, 4) NOT NULL,
  final_equity NUMERIC(19, 4) NOT NULL,
  total_return_pct NUMERIC(10, 4) NOT NULL,
  total_trades INTEGER NOT NULL,
  winning_trades INTEGER NOT NULL,
  losing_trades INTEGER NOT NULL,
  win_rate NUMERIC(10, 4) NOT NULL,
  max_drawdown_pct NUMERIC(10, 4) NOT NULL,
  sharpe_ratio NUMERIC(10, 4) NOT NULL,
  profit_factor NUMERIC(10, 4) NOT NULL,
  equity_curve JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX backtest_runs_strategy_idx ON backtest_runs (strategy_version_id, created_at DESC);
CREATE INDEX backtest_runs_symbol_idx ON backtest_runs (symbol, created_at DESC);

CREATE TABLE backtest_trades (
  id UUID PRIMARY KEY,
  backtest_id UUID NOT NULL REFERENCES backtest_runs(id),
  symbol VARCHAR(64) NOT NULL,
  side VARCHAR(16) NOT NULL CHECK (side IN ('BUY', 'SELL')),
  entry_time TIMESTAMPTZ NOT NULL,
  exit_time TIMESTAMPTZ NOT NULL,
  entry_price NUMERIC(19, 4) NOT NULL,
  exit_price NUMERIC(19, 4) NOT NULL,
  quantity NUMERIC(19, 4) NOT NULL,
  pnl NUMERIC(19, 4) NOT NULL,
  fee NUMERIC(19, 4) NOT NULL,
  return_pct NUMERIC(10, 4) NOT NULL,
  exit_reason VARCHAR(64) NOT NULL
);

CREATE INDEX backtest_trades_backtest_idx ON backtest_trades (backtest_id, entry_time);

CREATE TABLE walk_forward_runs (
  id UUID PRIMARY KEY,
  strategy_version_id UUID NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  timeframe VARCHAR(16) NOT NULL,
  window_count INTEGER NOT NULL,
  avg_oos_efficiency NUMERIC(10, 4) NOT NULL,
  windows_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX walk_forward_runs_strategy_idx ON walk_forward_runs (strategy_version_id, created_at DESC);
