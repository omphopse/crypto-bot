CREATE TABLE IF NOT EXISTS validated_trade_intents (
    id UUID PRIMARY KEY,
    decision_id UUID REFERENCES structured_trade_decisions(id),
    context_id UUID REFERENCES trading_contexts(id),
    context_hash VARCHAR(64) NOT NULL,
    bot_id UUID REFERENCES bots(id),
    session_id UUID REFERENCES agent_sessions(id),
    strategy_id UUID NOT NULL,
    strategy_version_id UUID NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity NUMERIC(30,12) NOT NULL,
    reference_price NUMERIC(30,12) NOT NULL,
    stop_loss NUMERIC(30,12),
    take_profit NUMERIC(30,12),
    time_horizon VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS strategy_validation_results (
    id UUID PRIMARY KEY,
    intent_id UUID REFERENCES validated_trade_intents(id),
    decision_id UUID REFERENCES structured_trade_decisions(id),
    passed BOOLEAN NOT NULL,
    reason TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS autonomous_execution_results (
    id UUID PRIMARY KEY,
    intent_id UUID REFERENCES validated_trade_intents(id),
    decision_id UUID REFERENCES structured_trade_decisions(id),
    risk_decision_id UUID,
    order_id UUID,
    status VARCHAR(64) NOT NULL,
    detail TEXT,
    executed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS validated_intents_bot_idx ON validated_trade_intents (bot_id, created_at DESC);
CREATE INDEX IF NOT EXISTS strategy_val_intent_idx ON strategy_validation_results (intent_id);
CREATE INDEX IF NOT EXISTS auto_exec_intent_idx ON autonomous_execution_results (intent_id);
