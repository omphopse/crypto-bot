CREATE TABLE IF NOT EXISTS ai_cost_events (
    cost_event_id UUID PRIMARY KEY,
    bot_id UUID REFERENCES bots(id),
    agent_session_id UUID,
    strategy_id UUID,
    strategy_version_id UUID,
    context_id UUID,
    decision_id UUID,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    estimated_input_cost NUMERIC(12, 6) NOT NULL,
    estimated_output_cost NUMERIC(12, 6) NOT NULL,
    estimated_total_cost NUMERIC(12, 6) NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'USD',
    timestamp TIMESTAMPTZ NOT NULL,
    latency_ms BIGINT NOT NULL,
    research_cost NUMERIC(12, 6) NOT NULL DEFAULT 0,
    token_attribution_json TEXT
);

CREATE TABLE IF NOT EXISTS ai_model_pricing (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    input_price_per_million NUMERIC(12, 4) NOT NULL,
    output_price_per_million NUMERIC(12, 4) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS ai_budget_policies (
    id UUID PRIMARY KEY,
    tier VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    max_cost_per_day NUMERIC(12, 4) NOT NULL,
    max_cost_per_hour NUMERIC(12, 4) NOT NULL,
    max_requests_per_day INT NOT NULL,
    max_requests_per_hour INT NOT NULL,
    CONSTRAINT uq_tier_target UNIQUE (tier, target_id)
);

CREATE INDEX IF NOT EXISTS ai_cost_bot_time_idx ON ai_cost_events (bot_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS ai_cost_time_idx ON ai_cost_events (timestamp DESC);
CREATE INDEX IF NOT EXISTS ai_cost_strat_idx ON ai_cost_events (strategy_id, timestamp DESC);

-- Seed default pricing
INSERT INTO ai_model_pricing (id, provider, model, input_price_per_million, output_price_per_million, effective_from, effective_to)
VALUES
    ('c1111111-1111-1111-1111-111111111111', 'fake-llm', 'mock-reasoner-v1', 0.50, 1.50, '2026-01-01 00:00:00+00', NULL),
    ('c2222222-2222-2222-2222-222222222222', 'openai', 'gpt-4o-mini', 0.15, 0.60, '2026-01-01 00:00:00+00', NULL)
ON CONFLICT (id) DO NOTHING;

-- Seed default global budget policy
INSERT INTO ai_budget_policies (id, tier, target_id, max_cost_per_day, max_cost_per_hour, max_requests_per_day, max_requests_per_hour)
VALUES
    ('b1111111-1111-1111-1111-111111111111', 'GLOBAL', 'GLOBAL', 25.00, 5.00, 1000, 200)
ON CONFLICT (tier, target_id) DO NOTHING;
