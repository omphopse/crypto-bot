package io.algopilot.agent.context;

public record FreshnessSummary(
    long marketFreshnessMs,
    long portfolioFreshnessMs,
    long riskFreshnessMs,
    FreshnessStatus overallStatus
) {}
