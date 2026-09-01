package io.algopilot.research.service;

import io.algopilot.research.model.ResearchDocument;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.model.ResearchRequest;
import io.algopilot.research.model.ResearchSource;
import io.algopilot.research.model.SecurityStatus;
import io.algopilot.research.model.SourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcResearchStore implements ResearchStore {
  private final JdbcTemplate jdbc;

  public JdbcResearchStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ResearchRequest saveRequest(ResearchRequest req) {
    jdbc.update(
        "INSERT INTO research_requests (id, session_id, bot_id, asset, topic, query, max_results, freshness_minutes, status, requested_at, completed_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, completed_at = EXCLUDED.completed_at",
        req.id(), req.sessionId(), req.botId(), req.asset(), req.topic(), req.query(),
        req.maxResults(), req.freshnessMinutes(), req.status(),
        Timestamp.from(req.requestedAt()),
        req.completedAt() != null ? Timestamp.from(req.completedAt()) : null
    );
    return req;
  }

  @Override
  public Optional<ResearchRequest> findRequestById(UUID id) {
    return jdbc.query("SELECT * FROM research_requests WHERE id = ?", this::mapRequest, id).stream().findFirst();
  }

  @Override
  public List<ResearchRequest> findRecentRequests(int limit) {
    return jdbc.query("SELECT * FROM research_requests ORDER BY requested_at DESC LIMIT ?", this::mapRequest, Math.max(1, limit));
  }

  @Override
  public ResearchSource saveSource(ResearchSource s) {
    jdbc.update(
        "INSERT INTO research_sources (id, domain, source_type, reliability_score, is_allowed) VALUES (?, ?, ?, ?, ?) " +
        "ON CONFLICT (domain) DO UPDATE SET reliability_score = EXCLUDED.reliability_score, is_allowed = EXCLUDED.is_allowed",
        s.id(), s.domain(), s.sourceType().name(), s.reliabilityScore(), s.isAllowed()
    );
    return s;
  }

  @Override
  public List<ResearchSource> findAllSources() {
    return jdbc.query("SELECT * FROM research_sources ORDER BY reliability_score DESC", this::mapSource);
  }

  @Override
  public Optional<ResearchSource> findSourceByDomain(String domain) {
    return jdbc.query("SELECT * FROM research_sources WHERE domain = ?", this::mapSource, domain.toLowerCase().trim()).stream().findFirst();
  }

  @Override
  public ResearchDocument saveDocument(ResearchDocument doc) {
    jdbc.update(
        "INSERT INTO research_documents (id, request_id, url, canonical_url, domain, title, retrieved_at, published_at, " +
        "content_hash, content_type, word_count, normalized_text, security_status) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        doc.id(), doc.requestId(), doc.url(), doc.canonicalUrl(), doc.domain(), doc.title(),
        Timestamp.from(doc.retrievedAt()),
        doc.publishedAt() != null ? Timestamp.from(doc.publishedAt()) : null,
        doc.contentHash(), doc.contentType(), doc.wordCount(), doc.normalizedText(), doc.securityStatus().name()
    );
    return doc;
  }

  @Override
  public List<ResearchDocument> findDocumentsByRequestId(UUID requestId) {
    return jdbc.query("SELECT * FROM research_documents WHERE request_id = ? ORDER BY retrieved_at DESC", this::mapDocument, requestId);
  }

  @Override
  public Optional<ResearchDocument> findDocumentByHash(String contentHash) {
    return jdbc.query("SELECT * FROM research_documents WHERE content_hash = ? LIMIT 1", this::mapDocument, contentHash).stream().findFirst();
  }

  @Override
  public ResearchEvidence saveEvidence(ResearchEvidence ev) {
    jdbc.update(
        "INSERT INTO research_evidence (id, document_id, request_id, source, asset, topic, excerpt, published_at, retrieved_at, relevance_score, security_status, content_hash) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        ev.id(), ev.documentId(), ev.requestId(), ev.source(), ev.asset(), ev.topic(), ev.excerpt(),
        ev.publishedAt() != null ? Timestamp.from(ev.publishedAt()) : null,
        Timestamp.from(ev.retrievedAt()),
        ev.relevanceScore(), ev.securityStatus().name(), ev.contentHash()
    );
    return ev;
  }

  @Override
  public List<ResearchEvidence> findEvidenceByRequestId(UUID requestId) {
    return jdbc.query("SELECT * FROM research_evidence WHERE request_id = ? ORDER BY retrieved_at DESC", this::mapEvidence, requestId);
  }

  @Override
  public List<ResearchEvidence> findEvidenceByAsset(String asset, int limit) {
    return jdbc.query("SELECT * FROM research_evidence WHERE asset = ? ORDER BY retrieved_at DESC LIMIT ?", this::mapEvidence, asset.toUpperCase(), Math.max(1, limit));
  }

  private ResearchRequest mapRequest(ResultSet rs, int rowNum) throws SQLException {
    return new ResearchRequest(
        rs.getObject("id", UUID.class),
        rs.getObject("session_id", UUID.class),
        rs.getObject("bot_id", UUID.class),
        rs.getString("asset"),
        rs.getString("topic"),
        rs.getString("query"),
        rs.getInt("max_results"),
        rs.getInt("freshness_minutes"),
        rs.getString("status"),
        rs.getTimestamp("requested_at").toInstant(),
        rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
    );
  }

  private ResearchSource mapSource(ResultSet rs, int rowNum) throws SQLException {
    return new ResearchSource(
        rs.getObject("id", UUID.class),
        rs.getString("domain"),
        SourceType.valueOf(rs.getString("source_type")),
        rs.getBigDecimal("reliability_score"),
        rs.getBoolean("is_allowed")
    );
  }

  private ResearchDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
    return new ResearchDocument(
        rs.getObject("id", UUID.class),
        rs.getObject("request_id", UUID.class),
        rs.getString("url"),
        rs.getString("canonical_url"),
        rs.getString("domain"),
        rs.getString("title"),
        rs.getTimestamp("retrieved_at").toInstant(),
        rs.getTimestamp("published_at") != null ? rs.getTimestamp("published_at").toInstant() : null,
        rs.getString("content_hash"),
        rs.getString("content_type"),
        rs.getInt("word_count"),
        rs.getString("normalized_text"),
        SecurityStatus.valueOf(rs.getString("security_status"))
    );
  }

  private ResearchEvidence mapEvidence(ResultSet rs, int rowNum) throws SQLException {
    return new ResearchEvidence(
        rs.getObject("id", UUID.class),
        rs.getObject("document_id", UUID.class),
        rs.getObject("request_id", UUID.class),
        rs.getString("source"),
        rs.getString("asset"),
        rs.getString("topic"),
        rs.getString("excerpt"),
        rs.getTimestamp("published_at") != null ? rs.getTimestamp("published_at").toInstant() : null,
        rs.getTimestamp("retrieved_at").toInstant(),
        rs.getBigDecimal("relevance_score"),
        SecurityStatus.valueOf(rs.getString("security_status")),
        rs.getString("content_hash")
    );
  }
}
