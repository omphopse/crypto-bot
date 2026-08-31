package io.algopilot.reconciliation.model;

import jakarta.validation.constraints.NotBlank;

public record RecoveryRequest(
    @NotBlank(message = "botId is required")
    String botId,
    String operatorId,
    String reason
) {}
