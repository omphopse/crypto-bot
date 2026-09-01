package io.algopilot.research.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResearchEvidence(
    UUID id,
    UUID documentId,
    UUID requestId,
    String source,
    String asset,
    String topic,
    String excerpt,
    Instant publishedAt,
    Instant retrievedAt,
    BigDecimal relevanceScore,
    SecurityStatus securityStatus,
    String contentHash
) {}
