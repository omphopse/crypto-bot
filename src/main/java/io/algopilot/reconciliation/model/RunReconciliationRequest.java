package io.algopilot.reconciliation.model;

import jakarta.validation.constraints.NotBlank;

public record RunReconciliationRequest(
    @NotBlank(message = "botId is required")
    String botId
) {}
