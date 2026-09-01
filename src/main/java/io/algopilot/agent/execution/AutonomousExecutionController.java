package io.algopilot.agent.execution;

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
@RequestMapping("/api/agent/autonomous")
public class AutonomousExecutionController {
  private final AutonomousExecutionOrchestrator orchestrator;
  private final AutonomousExecutionStore store;

  public AutonomousExecutionController(AutonomousExecutionOrchestrator orchestrator, AutonomousExecutionStore store) {
    this.orchestrator = orchestrator;
    this.store = store;
  }

  @PostMapping("/run/{botId}")
  public ResponseEntity<AutonomousExecutionResult> runAutonomousCycle(@PathVariable UUID botId) {
    AutonomousExecutionResult result = orchestrator.runCycle(botId);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/history/{botId}")
  public ResponseEntity<List<AutonomousExecutionResult>> getHistory(
      @PathVariable UUID botId,
      @RequestParam(defaultValue = "10") int limit) {
    return ResponseEntity.ok(store.findRecentExecutionsByBotId(botId, limit));
  }

  @GetMapping("/latest/{botId}")
  public ResponseEntity<AutonomousExecutionResult> getLatest(@PathVariable UUID botId) {
    return store.findLatestExecutionByBotId(botId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
