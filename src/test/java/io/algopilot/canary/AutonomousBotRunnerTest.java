package io.algopilot.canary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutonomousBotRunnerTest {
  private BotStore botStore;
  private AutonomousExecutionOrchestrator orchestrator;
  private PositionMonitorService positionMonitor;
  private LeaseManager leaseManager;
  private HeartbeatStore heartbeatStore;
  private AuditEventWriter audit;
  private Clock clock;
  private AutonomousBotRunner runner;

  private UUID botId;
  private Instant now;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    now = clock.instant();

    botStore = mock(BotStore.class);
    orchestrator = mock(AutonomousExecutionOrchestrator.class);
    positionMonitor = mock(PositionMonitorService.class);
    leaseManager = mock(LeaseManager.class);
    heartbeatStore = mock(HeartbeatStore.class);
    audit = mock(AuditEventWriter.class);

    runner = new AutonomousBotRunner(
        botStore, orchestrator, positionMonitor, leaseManager, heartbeatStore, audit, clock, "test-runner"
    );

    botId = UUID.randomUUID();
    Bot runningBot = new Bot(botId, "Canary Bot", UUID.randomUUID(), Broker.ALPACA_PAPER, ExecutionMode.PAPER, BotStatus.RUNNING, now);
    when(botStore.findById(botId)).thenReturn(Optional.of(runningBot));
    when(botStore.findAll()).thenReturn(List.of(runningBot));

    BotRuntimeLease lease = new BotRuntimeLease(botId, "test-runner", UUID.randomUUID(), now, now.plusSeconds(60), now);
    when(leaseManager.acquireLease(eq(botId), eq("test-runner"), any(Long.class))).thenReturn(Optional.of(lease));
    when(leaseManager.renewLease(eq(botId), eq(lease.leaseId()), eq("test-runner"), any(Long.class))).thenReturn(true);

    when(positionMonitor.monitorBotPositions(botId)).thenReturn(List.of());
    when(orchestrator.runCycle(botId)).thenReturn(new AutonomousExecutionResult(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "EXECUTED", "Order executed", now
    ));
  }

  @Test
  void testRunSingleBotCycle_executesCompleteContinuousLoop() {
    runner.runSingleBotCycle(botId);

    verify(leaseManager, times(1)).acquireLease(eq(botId), eq("test-runner"), any(Long.class));
    verify(heartbeatStore, times(1)).recordHeartbeat(any());
    verify(positionMonitor, times(1)).monitorBotPositions(botId);
    verify(orchestrator, times(1)).runCycle(botId);
    verify(audit, times(1)).record(eq("AGENT"), eq(botId.toString()), eq("AUTONOMOUS_CYCLE_COMPLETED"), eq("BOT"), eq(botId.toString()), any());
  }

  @Test
  void testRunSingleBotCycle_whenLeaseUnavailable_skipsCycle() {
    when(leaseManager.acquireLease(eq(botId), eq("test-runner"), any(Long.class))).thenReturn(Optional.empty());

    runner.runSingleBotCycle(botId);

    verify(heartbeatStore, never()).recordHeartbeat(any());
    verify(positionMonitor, never()).monitorBotPositions(botId);
    verify(orchestrator, never()).runCycle(botId);
  }
}
