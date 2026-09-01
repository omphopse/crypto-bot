package io.algopilot.research.model;

import java.time.Instant;
import java.util.UUID;

public record ResearchDocument(
    UUID id,
    UUID requestId,
    String url,
    String canonicalUrl,
    String domain,
    String title,
    Instant retrievedAt,
    Instant publishedAt,
    String contentHash,
    String contentType,
    int wordCount,
    String normalizedText,
    SecurityStatus securityStatus
) {}
