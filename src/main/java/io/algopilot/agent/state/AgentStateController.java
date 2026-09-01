package io.algopilot.agent.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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
@RequestMapping("/api/agent/state")
public class AgentStateController {
  private final AgentStateMachine stateMachine;
  private final AgentStateStore store;

  public AgentStateController(AgentStateMachine stateMachine, AgentStateStore store) {
    this.stateMachine = stateMachine;
    this.store = store;
  }

  @GetMapping("/sessions")
  public ResponseEntity<List<AgentSession>> getAllSessions() {
    return ResponseEntity.ok(store.findAllSessions());
  }

  @GetMapping("/sessions/{sessionId}")
  public ResponseEntity<AgentSession> getSession(@PathVariable UUID sessionId) {
    return store.findSessionById(sessionId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/sessions/{sessionId}/events")
  public ResponseEntity<List<AgentStateEvent>> getSessionEvents(
      @PathVariable UUID sessionId,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(store.findStateEventsBySessionId(sessionId, limit));
  }

  public record StartSessionRequest(UUID botId, String name, AutonomousMode mode, JsonNode configuration) {}

  @PostMapping("/sessions")
  public ResponseEntity<AgentSession> startSession(@RequestBody StartSessionRequest request) {
    AgentSession session = stateMachine.startSession(
        request.botId(),
        request.name(),
        request.mode(),
        request.configuration()
    );
    return ResponseEntity.ok(session);
  }

  public record ActionRequest(String reason) {}

  @PostMapping("/sessions/{sessionId}/pause")
  public ResponseEntity<AgentSession> pauseSession(@PathVariable UUID sessionId, @RequestBody(required = false) ActionRequest request) {
    String reason = request != null ? request.reason() : "Operator pause";
    return ResponseEntity.ok(stateMachine.pauseSession(sessionId, reason));
  }

  @PostMapping("/sessions/{sessionId}/resume")
  public ResponseEntity<AgentSession> resumeSession(@PathVariable UUID sessionId, @RequestBody(required = false) ActionRequest request) {
    String reason = request != null ? request.reason() : "Operator resume";
    return ResponseEntity.ok(stateMachine.resumeSession(sessionId, reason));
  }

  @PostMapping("/sessions/{sessionId}/stop")
  public ResponseEntity<AgentSession> stopSession(@PathVariable UUID sessionId, @RequestBody(required = false) ActionRequest request) {
    String reason = request != null ? request.reason() : "Operator stop";
    return ResponseEntity.ok(stateMachine.stopSession(sessionId, reason));
  }
}
