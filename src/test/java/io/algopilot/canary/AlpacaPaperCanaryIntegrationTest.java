package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.agent.execution.AutonomousExecutionOrchestrator;
import io.algopilot.agent.execution.AutonomousExecutionResult;
import io.algopilot.agent.position.PositionMonitorService;
import io.algopilot.agent.runtime.AutonomousBotRunner;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.bot.Broker;
import io.algopilot.bot.ExecutionMode;
import io.algopilot.ops.health.ComponentType;
import io.algopilot.ops.health.HealthState;
import io.algopilot.ops.health.HeartbeatRecord;
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseManager;
import io.algopilot.strategy.CreateStrategyRequest;
import io.algopilot.strategy.StrategyService;
import io.algopilot.strategy.StrategyStore;
import io.algopilot.strategy.StrategyVersion;
import io.algopilot.strategy.discovery.model.CandidateStatus;
import io.algopilot.strategy.discovery.model.GenerationMethod;
import io.algopilot.strategy.discovery.model.RobustnessTag;
import io.algopilot.strategy.discovery.model.StrategyCandidate;
import io.algopilot.strategy.discovery.model.StrategyFamily;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import io.algopilot.strategy.discovery.service.CandidatePromotionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlpacaPaperCanaryIntegrationTest {
  private CandidateStore candidateStore;
  private StrategyService strategyService;
  private BotStore botStore;
  private AuditEventWriter audit;
  private ObjectMapper objectMapper;
  private Clock clock;
  private Instant now;

  private CandidatePromotionService promotionService;
  private AutonomousBotRunner botRunner;
  private AutonomousExecutionOrchestrator orchestrator;
  private PositionMonitorService positionMonitor;
  private LeaseManager leaseManager;
  private HeartbeatStore heartbeatStore;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    candidateStore = mock(CandidateStore.class);
    strategyService = mock(StrategyService.class);
    botStore = mock(BotStore.class);
    audit = mock(AuditEventWriter.class);
    objectMapper = new ObjectMapper();

    orchestrator = mock(AutonomousExecutionOrchestrator.class);
    positionMonitor = mock(PositionMonitorService.class);
    leaseManager = mock(LeaseManager.class);
    heartbeatStore = mock(HeartbeatStore.class);

    promotionService = new CandidatePromotionService(
        candidateStore, strategyService, botStore, audit, objectMapper, clock
    );

    botRunner = new AutonomousBotRunner(
        botStore, orchestrator, positionMonitor, leaseManager, heartbeatStore, audit, clock, "canary-runtime-01"
    );
  }

  @Test
  void testEndToEndQualifiedCandidateSelectionPromotionAndCanaryRun() {
    // 1. Setup a verified ROBUST candidate
    UUID candidateId = UUID.randomUUID();
    UUID baseStrategyId = UUID.randomUUID();
    StrategyCandidate robustCandidate = new StrategyCandidate(
        candidateId,
        "fp-canary-001",
        baseStrategyId,
        "MOMENTUM-BTC-F9-S21-R45",
        StrategyFamily.MOMENTUM,
        "BTC/USD",
        "1h",
        Map.of("fastEma", "9", "slowEma", "21", "rsiThreshold", "45"),
        GenerationMethod.PARAMETER_SWEEP,
        CandidateStatus.PAPER_PENDING,
        RobustnessTag.ROBUST,
        new BigDecimal("88.50"),
        new BigDecimal("0.0035"), // +35 bps net expectancy
        new BigDecimal("1.85"),   // profit factor
        new BigDecimal("5.20"),   // 5.2% max drawdown
        now
    );

    when(candidateStore.findAllCandidates()).thenReturn(List.of(robustCandidate));
    when(candidateStore.findCandidateById(candidateId)).thenReturn(Optional.of(robustCandidate));
    when(candidateStore.saveCandidate(any())).thenAnswer(inv -> inv.getArgument(0));

    UUID stratVerId = UUID.randomUUID();
    StrategyVersion stratVersion = new StrategyVersion(
        stratVerId, baseStrategyId, 1, objectMapper.createObjectNode(), "Canary deploy", now
    );
    when(strategyService.create(any(CreateStrategyRequest.class))).thenReturn(stratVersion);

    // 2. Select Candidate
    CandidatePromotionService.QualificationCheckResult qualResult = promotionService.selectQualifiedCandidate();
    assertThat(qualResult.qualified()).isTrue();
    assertThat(qualResult.candidate().candidateId()).isEqualTo(candidateId);

    // 3. Promote Candidate to Alpaca Paper
    CandidatePromotionService.CanaryDeploymentResult deployResult = promotionService.promoteToPaper(candidateId);
    assertThat(deployResult.candidateId()).isEqualTo(candidateId);
    assertThat(deployResult.broker()).isEqualTo("ALPACA_PAPER");
    assertThat(deployResult.executionMode()).isEqualTo("PAPER");

    UUID botId = deployResult.botId();
    Bot canaryBot = new Bot(
        botId, "Canary-Alpaca-BTC-USD", stratVerId, Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now
    );
    when(botStore.findById(botId)).thenReturn(Optional.of(canaryBot));
    when(botStore.findAll()).thenReturn(List.of(canaryBot));

    BotRuntimeLease lease = new BotRuntimeLease(botId, "canary-runtime-01", UUID.randomUUID(), now, now.plusSeconds(180), now);
    when(leaseManager.acquireLease(eq(botId), eq("canary-runtime-01"), any(Long.class))).thenReturn(Optional.of(lease));
    when(leaseManager.renewLease(eq(botId), eq(lease.leaseId()), eq("canary-runtime-01"), any(Long.class))).thenReturn(true);

    when(positionMonitor.monitorBotPositions(botId)).thenReturn(List.of());
    when(orchestrator.runCycle(botId)).thenReturn(new AutonomousExecutionResult(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "EXECUTED", "Canary order executed", now
    ));

    // 4. Run Autonomous Canary Cycle
    botRunner.runSingleBotCycle(botId);

    // 5. Verify Invariants
    verify(leaseManager).acquireLease(eq(botId), eq("canary-runtime-01"), any(Long.class));
    verify(heartbeatStore).recordHeartbeat(any(HeartbeatRecord.class));
    verify(positionMonitor).monitorBotPositions(botId);
    verify(orchestrator).runCycle(botId);
    verify(audit).record(eq("AGENT"), eq(botId.toString()), eq("AUTONOMOUS_CYCLE_COMPLETED"), eq("BOT"), eq(botId.toString()), any());
  }

  @Test
  void testSelectQualifiedCandidate_whenNoCandidateQualifies_returnsNoQualifiedCandidate() {
    StrategyCandidate fragileCandidate = new StrategyCandidate(
        UUID.randomUUID(), "fp-fragile", UUID.randomUUID(), "FRAGILE-BTC",
        StrategyFamily.MOMENTUM, "BTC/USD", "1h", Map.of(), GenerationMethod.MANUAL,
        CandidateStatus.REJECTED, RobustnessTag.FRAGILE, new BigDecimal("45.0"),
        new BigDecimal("-0.0010"), new BigDecimal("0.90"), new BigDecimal("25.0"), now
    );
    when(candidateStore.findAllCandidates()).thenReturn(List.of(fragileCandidate));

    CandidatePromotionService.QualificationCheckResult qualResult = promotionService.selectQualifiedCandidate();
    assertThat(qualResult.qualified()).isFalse();
    assertThat(qualResult.failureReason()).contains("NO_QUALIFIED_CANDIDATE");
  }
}
