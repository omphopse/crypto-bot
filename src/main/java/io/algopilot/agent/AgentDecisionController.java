package io.algopilot.agent;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/decisions")
public class AgentDecisionController {
  private final DecisionJournalService service;
  private final AgentDecisionStore store;

  public AgentDecisionController(DecisionJournalService service, AgentDecisionStore store) {
    this.service = service;
    this.store = store;
  }

  @org.springframework.web.bind.annotation.GetMapping
  public java.util.List<AgentDecision> list(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit) {
    return store.findRecent(limit);
  }

  @PostMapping public ResponseEntity<AgentDecision> journal(@Valid @RequestBody StructuredDecisionRequest request) { AgentDecision decision = service.journal(request); return ResponseEntity.created(URI.create("/api/agent/decisions/" + decision.id())).body(decision); }
  @ExceptionHandler(DecisionValidationException.class) ResponseEntity<?> invalid(DecisionValidationException error) { return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage())); }
}
