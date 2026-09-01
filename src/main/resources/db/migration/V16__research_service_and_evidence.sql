CREATE TABLE IF NOT EXISTS research_requests (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES agent_sessions(id),
    bot_id UUID REFERENCES bots(id),
    asset VARCHAR(64) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    query VARCHAR(512) NOT NULL,
    max_results INT NOT NULL,
    freshness_minutes INT NOT NULL,
    status VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS research_requests_asset_idx ON research_requests (asset, requested_at DESC);
CREATE INDEX IF NOT EXISTS research_requests_session_idx ON research_requests (session_id);

CREATE TABLE IF NOT EXISTS research_sources (
    id UUID PRIMARY KEY,
    domain VARCHAR(256) NOT NULL UNIQUE,
    source_type VARCHAR(64) NOT NULL,
    reliability_score NUMERIC(5,2) NOT NULL,
    is_allowed BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS research_documents (
    id UUID PRIMARY KEY,
    request_id UUID REFERENCES research_requests(id),
    url VARCHAR(1024) NOT NULL,
    canonical_url VARCHAR(1024) NOT NULL,
    domain VARCHAR(256) NOT NULL,
    title VARCHAR(512),
    retrieved_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    content_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    word_count INT NOT NULL,
    normalized_text TEXT NOT NULL,
    security_status VARCHAR(32) NOT NULL
);

CREATE INDEX IF NOT EXISTS research_documents_domain_idx ON research_documents (domain);
CREATE INDEX IF NOT EXISTS research_documents_hash_idx ON research_documents (content_hash);

CREATE TABLE IF NOT EXISTS research_evidence (
    id UUID PRIMARY KEY,
    document_id UUID REFERENCES research_documents(id),
    request_id UUID REFERENCES research_requests(id),
    source VARCHAR(256) NOT NULL,
    asset VARCHAR(64) NOT NULL,
    topic VARCHAR(64) NOT NULL,
    excerpt TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    retrieved_at TIMESTAMPTZ NOT NULL,
    relevance_score NUMERIC(5,2) NOT NULL,
    security_status VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS research_evidence_asset_topic_idx ON research_evidence (asset, topic, retrieved_at DESC);
