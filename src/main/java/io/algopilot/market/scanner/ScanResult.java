package io.algopilot.market.scanner;

import io.algopilot.market.observation.MarketObservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of a deterministic market scan evaluation.
 * Note: MarketScanner outputs opportunities/candidates only and CANNOT trade.
 */
public record ScanResult(
    UUID id,
    UUID sessionId,
    UUID botId,
    String symbol,
    String timeframe,
    String provider,
    CandidateType candidateType,
    List<String> triggerConditions,
    IndicatorSnapshot indicatorSnapshot,
    MarketObservation marketObservation,
    BigDecimal confidenceScore,
    String reason,
    String status,
    Instant timestamp
) {}
