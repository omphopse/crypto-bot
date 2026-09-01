package io.algopilot.agent.runtime;

import io.algopilot.agent.execution.AutonomousExecutionOrchestrator;
import io.algopilot.agent.execution.AutonomousExecutionResult;
import io.algopilot.agent.position.PositionExitEvent;
import io.algopilot.agent.position.PositionMonitorService;
import io.algopilot.audit.AuditEventWriter;
import io.algopilot.bot.Bot;
import io.algopilot.bot.BotStatus;
import io.algopilot.bot.BotStore;
import io.algopilot.ops.health.ComponentType;
import io.algopilot.ops.health.HealthState;
import io.algopilot.ops.health.HeartbeatRecord;
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.lease.BotRuntimeLease;
import io.algopilot.ops.lease.LeaseManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AutonomousBotRunner {
  private static final Logger log = LoggerFactory.getLogger(AutonomousBotRunner.class);

  private final BotStore botStore;
  private final AutonomousExecutionOrchestrator executionOrchestrator;
  private final PositionMonitorService positionMonitorService;
  private final LeaseManager leaseManager;
  private final HeartbeatStore heartbeatStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  private final String instanceId;
  private final AtomicBoolean isRunning = new AtomicBoolean(true);
  private final ConcurrentHashMap<UUID, Boolean> activeCycleMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, UUID> botLeaseMap = new ConcurrentHashMap<>();
  private final AtomicLong cycleCounter = new AtomicLong(0);

  private RuntimeCadence cadence = RuntimeCadence.NORMAL;

  public AutonomousBotRunner(
      BotStore botStore,
      AutonomousExecutionOrchestrator executionOrchestrator,
      PositionMonitorService positionMonitorService,
      LeaseManager leaseManager,
      HeartbeatStore heartbeatStore,
      AuditEventWriter audit,
      Clock clock,
      @Value("${algopilot.runtime.instance-id:runtime-primary}") String instanceId
  ) {
    this.botStore = botStore;
    this.executionOrchestrator = executionOrchestrator;
    this.positionMonitorService = positionMonitorService;
    this.leaseManager = leaseManager;
    this.heartbeatStore = heartbeatStore;
    this.audit = audit;
    this.clock = clock;
    this.instanceId = instanceId;
  }

  public AutonomousBotRunner(
      BotStore botStore,
      AutonomousExecutionOrchestrator executionOrchestrator,
      PositionMonitorService positionMonitorService,
      LeaseManager leaseManager,
      HeartbeatStore heartbeatStore,
      AuditEventWriter audit,
      Clock clock
  ) {
    this(botStore, executionOrchestrator, positionMonitorService, leaseManager, heartbeatStore, audit, clock, "runtime-primary");
  }

  public void start() {
    isRunning.set(true);
    log.info("AUTONOMOUS_BOT_RUNNER_STARTED instanceId={}", instanceId);
  }

  public void stop() {
    isRunning.set(false);
    log.info("AUTONOMOUS_BOT_RUNNER_STOPPED instanceId={}", instanceId);
  }

  public boolean isRunning() {
    return isRunning.get();
  }

  public void setCadence(RuntimeCadence cadence) {
    this.cadence = cadence;
  }

  public RuntimeCadence getCadence() {
    return this.cadence;
  }

  @Scheduled(fixedDelayString = "${algopilot.runtime.interval-ms:15000}")
  public void executeScheduledRun() {
    if (!isRunning.get()) {
      return;
    }

    List<Bot> activeBots = botStore.findAll().stream()
        .filter(b -> b.status() == BotStatus.RUNNING)
        .toList();

    for (Bot bot : activeBots) {
      runSingleBotCycle(bot.id());
    }
  }

  public void runSingleBotCycle(UUID botId) {
    // 1. Prevent Overlapping Cycles
    if (activeCycleMap.putIfAbsent(botId, Boolean.TRUE) != null) {
      log.warn("SKIPPING_OVERLAPPING_CYCLE for botId={}", botId);
      return;
    }

    try {
      Instant now = clock.instant();
      Optional<Bot> botOpt = botStore.findById(botId);
      if (botOpt.isEmpty() || botOpt.get().status() != BotStatus.RUNNING) {
        return;
      }

      // 2. Multi-Instance Lease Acquisition / Renewal
      UUID leaseId = botLeaseMap.get(botId);
      if (leaseId == null) {
        Optional<BotRuntimeLease> leaseOpt = leaseManager.acquireLease(botId, instanceId, cadence.intervalMs() * 3);
        if (leaseOpt.isEmpty()) {
          log.warn("CANNOT_RUN_CYCLE for botId={} - lease held by another instance", botId);
          return;
        }
        leaseId = leaseOpt.get().leaseId();
        botLeaseMap.put(botId, leaseId);
      } else {
        boolean renewed = leaseManager.renewLease(botId, leaseId, instanceId, cadence.intervalMs() * 3);
        if (!renewed) {
          botLeaseMap.remove(botId);
          log.warn("LEASE_RENEWAL_FAILED for botId={} during runner cycle", botId);
          return;
        }
      }

      // 3. Emit Heartbeat
      long seq = cycleCounter.incrementAndGet();
      heartbeatStore.recordHeartbeat(new HeartbeatRecord(
          UUID.randomUUID(), ComponentType.BOT_RUNTIME, instanceId, botId, seq, HealthState.HEALTHY, now, null
      ));

      // 4. Run Position Monitor (Safety Exits / Trailing Stops / MFE)
      if (positionMonitorService != null) {
        List<PositionExitEvent> exits = positionMonitorService.monitorBotPositions(botId);
        if (!exits.isEmpty()) {
          log.info("POSITION_MONITOR_EXITS executed count={} for botId={}", exits.size(), botId);
        }
      }

      // 5. Run Execution Orchestrator (Observe / Scan / LLM / Strategy / Risk / Execute)
      AutonomousExecutionResult execResult = executionOrchestrator.runCycle(botId);
      log.info("AUTONOMOUS_CYCLE_RESULT botId={} status={}", botId, execResult.status());

      // 6. Record Audit Event
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_CYCLE_COMPLETED", "BOT", botId.toString(),
          Map.of("status", execResult.status(), "sequence", String.valueOf(seq)));

    } catch (Exception e) {
      log.error("AUTONOMOUS_CYCLE_EXCEPTION for botId={}: {}", botId, e.getMessage(), e);
      audit.record("AGENT", botId.toString(), "AUTONOMOUS_CYCLE_ERROR", "BOT", botId.toString(),
          Map.of("error", e.getMessage() != null ? e.getMessage() : "UNKNOWN_ERROR"));
    } finally {
      activeCycleMap.remove(botId);
    }
  }
}
