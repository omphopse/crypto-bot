package io.algopilot.ops.recovery;

import java.time.Instant;
import java.util.UUID;

public record RecoveryRun(
    UUID id,
    UUID botId,
    String instanceId,
    RecoveryStatus status,
    String triggerReason,
    String stepDetails,
    Instant startedAt,
    Instant completedAt
) {}
