package io.algopilot.agent.context;

import java.util.List;

public record SafetySummary(
    boolean marketDataValid,
    boolean marketDataFresh,
    boolean riskStateValid,
    boolean reconciliationHealthy,
    boolean strategyActive,
    boolean executionAllowed,
    List<String> safetyBlockReasons
) {}
