package io.algopilot.agent.position;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/position")
public class PositionMonitorController {
  private final PositionMonitorService monitorService;
  private final PositionLifecycleStore lifecycleStore;

  public PositionMonitorController(PositionMonitorService monitorService, PositionLifecycleStore lifecycleStore) {
    this.monitorService = monitorService;
    this.lifecycleStore = lifecycleStore;
  }

  @PostMapping("/monitor/{botId}")
  public ResponseEntity<List<PositionExitEvent>> monitorPositions(@PathVariable UUID botId) {
    List<PositionExitEvent> events = monitorService.monitorBotPositions(botId);
    return ResponseEntity.ok(events);
  }

  @GetMapping("/open/{botId}")
  public ResponseEntity<List<PositionLifecycleRecord>> getOpenPositions(@PathVariable UUID botId) {
    return ResponseEntity.ok(lifecycleStore.findOpenLifecyclesByBotId(botId));
  }

  @GetMapping("/snapshots/{positionId}")
  public ResponseEntity<List<PositionSnapshot>> getSnapshots(
      @PathVariable UUID positionId,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(lifecycleStore.findSnapshotsByPositionId(positionId, limit));
  }

  @GetMapping("/stops/{positionId}")
  public ResponseEntity<List<PositionStopRecord>> getStopHistory(@PathVariable UUID positionId) {
    return ResponseEntity.ok(lifecycleStore.findStopHistoryByPositionId(positionId));
  }

  @GetMapping("/history/{positionId}")
  public ResponseEntity<List<PositionExitEvent>> getExitHistory(@PathVariable UUID positionId) {
    return ResponseEntity.ok(lifecycleStore.findExitEventsByPositionId(positionId));
  }
}
