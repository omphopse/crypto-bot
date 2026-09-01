package io.algopilot.canary;

import io.algopilot.agent.execution.AutonomousExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canary")
public class CanaryController {
  private final CanaryService canaryService;

  public CanaryController(CanaryService canaryService) {
    this.canaryService = canaryService;
  }

  @GetMapping("/status")
  public ResponseEntity<List<CanaryStatus>> getCanaryStatus() {
    return ResponseEntity.ok(canaryService.getCanaryStatus());
  }

  @PostMapping("/start")
  public ResponseEntity<Map<String, String>> startCanary() {
    canaryService.startRunner();
    return ResponseEntity.ok(Map.of("status", "STARTED", "message", "Autonomous canary runner started."));
  }

  @PostMapping("/stop")
  public ResponseEntity<Map<String, String>> stopCanary() {
    canaryService.stopRunner();
    return ResponseEntity.ok(Map.of("status", "STOPPED", "message", "Autonomous canary runner paused."));
  }

  @PostMapping("/cycle/{botId}")
  public ResponseEntity<AutonomousExecutionResult> runCanaryCycle(@PathVariable UUID botId) {
    AutonomousExecutionResult result = canaryService.runCanaryCycle(botId);
    return ResponseEntity.ok(result);
  }
}
