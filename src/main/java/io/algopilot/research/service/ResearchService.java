package io.algopilot.research.service;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.research.model.ResearchDocument;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.model.ResearchRequest;
import io.algopilot.research.model.ResearchResult;
import io.algopilot.research.model.SecurityStatus;
import io.algopilot.research.provider.ResearchProvider;
import io.algopilot.research.security.ContentSanitizer;
import io.algopilot.research.security.PromptInjectionDetector;
import io.algopilot.research.security.ResearchRateLimiter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResearchService {
  private static final Logger log = LoggerFactory.getLogger(ResearchService.class);

  private final ResearchStore store;
  private final ContentSanitizer sanitizer;
  private final PromptInjectionDetector injectionDetector;
  private final ResearchRateLimiter rateLimiter;
  private final AuditEventWriter audit;
  private final Clock clock;

  public ResearchService(
      ResearchStore store,
      ContentSanitizer sanitizer,
      PromptInjectionDetector injectionDetector,
      ResearchRateLimiter rateLimiter,
      AuditEventWriter audit,
      Clock clock
  ) {
    this.store = store;
    this.sanitizer = sanitizer;
    this.injectionDetector = injectionDetector;
    this.rateLimiter = rateLimiter;
    this.audit = audit;
    this.clock = clock;
  }

  public ResearchResult executeResearch(ResearchRequest request, List<ResearchProvider.RawPage> rawPages) {
    Instant now = clock.instant();

    if (!rateLimiter.tryAcquire()) {
      log.warn("Research rate limit exceeded for asset: {}", request.asset());
      ResearchRequest rateLimitedReq = new ResearchRequest(
          request.id(), request.sessionId(), request.botId(), request.asset(), request.topic(),
          request.query(), request.maxResults(), request.freshnessMinutes(), "RATE_LIMITED",
          request.requestedAt(), now
      );
      store.saveRequest(rateLimitedReq);
      audit.record("RESEARCH", request.asset(), "RESEARCH_FAILED", "REQUEST", request.id().toString(),
          Map.of("reason", "RATE_LIMITED"));
      return new ResearchResult(UUID.randomUUID(), rateLimitedReq, List.of(), List.of(), "RATE_LIMITED", now);
    }

    ResearchRequest activeReq = new ResearchRequest(
        request.id(), request.sessionId(), request.botId(), request.asset(), request.topic(),
        request.query(), request.maxResults(), request.freshnessMinutes(), "IN_PROGRESS",
        request.requestedAt(), null
    );
    store.saveRequest(activeReq);
    audit.record("RESEARCH", request.asset(), "RESEARCH_STARTED", "REQUEST", request.id().toString(),
        Map.of("query", request.query(), "topic", request.topic()));

    List<ResearchDocument> documents = new ArrayList<>();
    List<ResearchEvidence> evidenceList = new ArrayList<>();

    if (rawPages != null) {
      for (ResearchProvider.RawPage page : rawPages) {
        String contentHash = computeHash(page.rawContent());
        String normalizedText = sanitizer.sanitize(page.rawContent());
        PromptInjectionDetector.Analysis analysis = injectionDetector.analyze(page.rawContent() + " " + normalizedText);

        SecurityStatus secStatus = analysis.status();
        if (secStatus == SecurityStatus.BLOCKED || secStatus == SecurityStatus.SUSPICIOUS) {
          log.warn("Prompt injection pattern detected in page {}: status={}, rules={}", page.url(), secStatus, analysis.flaggedRules());
          audit.record("RESEARCH", request.asset(), "RESEARCH_PROMPT_INJECTION_DETECTED", "DOCUMENT",
              contentHash, Map.of("url", page.url(), "status", secStatus.name(), "rules", String.join(", ", analysis.flaggedRules())));
        }

        int wordCount = normalizedText.isEmpty() ? 0 : normalizedText.split("\\s+").length;
        String domain = extractDomain(page.url());

        ResearchDocument doc = new ResearchDocument(
            UUID.randomUUID(), request.id(), page.url(), page.url(), domain, page.title(),
            now, page.publishedAt() != null ? page.publishedAt() : now,
            contentHash, "text/html", wordCount, normalizedText, secStatus
        );
        store.saveDocument(doc);
        documents.add(doc);

        // Extract excerpt for evidence
        String excerpt = normalizedText.length() > 500 ? normalizedText.substring(0, 500) + "..." : normalizedText;
        BigDecimal relevance = calculateRelevance(request.query(), normalizedText);

        ResearchEvidence evidence = new ResearchEvidence(
            UUID.randomUUID(), doc.id(), request.id(), domain, request.asset(), request.topic(),
            excerpt, doc.publishedAt(), now, relevance, secStatus, contentHash
        );
        store.saveEvidence(evidence);
        evidenceList.add(evidence);
      }
    }

    ResearchRequest completedReq = new ResearchRequest(
        request.id(), request.sessionId(), request.botId(), request.asset(), request.topic(),
        request.query(), request.maxResults(), request.freshnessMinutes(), "COMPLETED",
        request.requestedAt(), now
    );
    store.saveRequest(completedReq);

    audit.record("RESEARCH", request.asset(), "RESEARCH_COMPLETED", "REQUEST", request.id().toString(),
        Map.of("documentsCount", String.valueOf(documents.size()), "evidenceCount", String.valueOf(evidenceList.size())));

    return new ResearchResult(UUID.randomUUID(), completedReq, documents, evidenceList, "COMPLETED", now);
  }

  private String computeHash(String text) {
    if (text == null) text = "";
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not supported", e);
    }
  }

  private String extractDomain(String urlString) {
    try {
      java.net.URI uri = java.net.URI.create(urlString);
      String host = uri.getHost();
      return host != null ? host : "unknown";
    } catch (Exception e) {
      return "unknown";
    }
  }

  private BigDecimal calculateRelevance(String query, String content) {
    if (query == null || content == null) return BigDecimal.ZERO;
    String[] terms = query.toLowerCase().split("\\s+");
    int matches = 0;
    String lowerContent = content.toLowerCase();
    for (String term : terms) {
      if (lowerContent.contains(term)) matches++;
    }
    double score = terms.length > 0 ? (double) matches / terms.length : 0.5;
    return BigDecimal.valueOf(Math.min(1.0, score)).setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
