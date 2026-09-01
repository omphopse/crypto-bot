package io.algopilot.agent.context;

import io.algopilot.research.model.SecurityStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResearchEvidenceContext(
    UUID evidenceId,
    String asset,
    String topic,
    String sourceDomain,
    String excerpt,
    BigDecimal relevanceScore,
    SecurityStatus securityStatus,
    Instant retrievedAt,
    boolean isUntrustedExternalData
) {}
