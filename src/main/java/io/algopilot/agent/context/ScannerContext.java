package io.algopilot.agent.context;

import io.algopilot.market.scanner.CandidateType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ScannerContext(
    CandidateType candidateType,
    BigDecimal confidenceScore,
    List<String> triggerConditions,
    String reason,
    String status,
    Instant scanTimestamp
) {}
