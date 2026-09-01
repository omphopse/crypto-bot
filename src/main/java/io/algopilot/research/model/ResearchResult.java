package io.algopilot.research.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResearchResult(
    UUID id,
    ResearchRequest request,
    List<ResearchDocument> documents,
    List<ResearchEvidence> evidence,
    String status,
    Instant completedAt
) {}
