package io.algopilot.ops;

import io.algopilot.ops.health.ComponentType;
import io.algopilot.ops.health.HealthEvent;
import io.algopilot.ops.health.HealthState;
import io.algopilot.ops.health.HeartbeatRecord;
import io.algopilot.ops.health.HeartbeatStore;
import io.algopilot.ops.recovery.RecoveryRun;
import io.algopilot.ops.recovery.RecoveryService;
import io.algopilot.ops.recovery.RecoveryStore;
import io.algopilot.ops.watchdog.WatchdogAlert;
import io.algopilot.ops.watchdog.WatchdogService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthWatchdogController {
  private final HeartbeatStore heartbeatStore;
  private final WatchdogService watchdogService;
  private final RecoveryService recoveryService;
  private final RecoveryStore recoveryStore;

  public HealthWatchdogController(
      HeartbeatStore heartbeatStore,
      WatchdogService watchdogService,
      RecoveryService recoveryService,
      RecoveryStore recoveryStore
  ) {
    this.heartbeatStore = heartbeatStore;
    this.watchdogService = watchdogService;
    this.recoveryService = recoveryService;
    this.recoveryStore = recoveryStore;
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> getOverallHealth() {
    List<HeartbeatRecord> heartbeats = heartbeatStore.findAllActiveHeartbeats();
    boolean allHealthy = heartbeats.stream().allMatch(h -> h.status() == HealthState.HEALTHY);

    return ResponseEntity.ok(Map.of(
        "status", allHealthy ? "UP" : "DEGRADED",
        "liveTradingDisabled", true,
        "activeComponents", heartbeats.size(),
        "timestamp", Instant.now().toString()
    ));
  }

  @GetMapping("/components")
  public ResponseEntity<List<HeartbeatRecord>> getComponentHeartbeats() {
    return ResponseEntity.ok(heartbeatStore.findAllActiveHeartbeats());
  }

  @GetMapping("/bots/{botId}")
  public ResponseEntity<List<HeartbeatRecord>> getBotHeartbeats(@PathVariable UUID botId) {
    return ResponseEntity.ok(heartbeatStore.findHeartbeatsByBotId(botId));
  }

  @GetMapping("/watchdog")
  public ResponseEntity<List<WatchdogAlert>> runWatchdogCheck() {
    List<WatchdogAlert> alerts = watchdogService.checkSystemHealth();
    return ResponseEntity.ok(alerts);
  }

  @GetMapping("/recovery/{botId}")
  public ResponseEntity<RecoveryRun> getLatestRecovery(@PathVariable UUID botId) {
    return recoveryStore.findLatestRunByBotId(botId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/recovery/trigger/{botId}")
  public ResponseEntity<RecoveryRun> triggerRecovery(
      @PathVariable UUID botId,
      @RequestParam(defaultValue = "manual-operator") String instanceId,
      @RequestParam(defaultValue = "OPERATOR_TRIGGERED") String reason) {
    RecoveryRun run = recoveryService.runRecovery(botId, instanceId, reason);
    return ResponseEntity.ok(run);
  }

  @PostMapping("/heartbeat")
  public ResponseEntity<HeartbeatRecord> postHeartbeat(@RequestBody HeartbeatRecord request) {
    HeartbeatRecord recorded = heartbeatStore.recordHeartbeat(new HeartbeatRecord(
        UUID.randomUUID(), request.component(), request.instanceId(), request.botId(),
        request.sequenceNumber(), request.status(), Instant.now(), request.metadataJson()
    ));
    return ResponseEntity.ok(recorded);
  }

  @GetMapping("/events")
  public ResponseEntity<List<HealthEvent>> getRecentEvents(@RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(heartbeatStore.findRecentHealthEvents(limit));
  }
}
