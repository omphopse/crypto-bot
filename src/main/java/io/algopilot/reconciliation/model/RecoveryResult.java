package io.algopilot.reconciliation.model;

import java.time.Instant;
import java.util.UUID;

public record RecoveryResult(
    UUID recoveryId,
    String botId,
    String status,
    String message,
    Instant recoveredAt
) {}
