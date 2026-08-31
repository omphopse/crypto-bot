package io.algopilot.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecisionEvidence(@NotBlank String referenceId, @NotNull EvidenceKind kind, @NotBlank String summary) {}
