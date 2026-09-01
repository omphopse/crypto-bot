package io.algopilot.agent.context;

import io.algopilot.agent.state.AgentState;
import io.algopilot.agent.state.AutonomousMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradingContext(
    UUID contextId,
    String contextHash,
    Instant generatedAt,
    UUID botId,
    UUID agentSessionId,
    String provider,
    String environment,
    AgentState agentState,
    AutonomousMode autonomousMode,
    MarketContext market,
    IndicatorContext indicators,
    ScannerContext scanner,
    StrategyContext strategy,
    PortfolioContext portfolio,
    List<PositionContext> positions,
    List<OpenOrderContext> openOrders,
    RiskContext risk,
    ReconciliationContext reconciliation,
    List<ResearchEvidenceContext> research,
    PerformanceContext performance,
    FreshnessSummary freshness,
    SafetySummary safety
) {}
