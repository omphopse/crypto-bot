package io.algopilot.agent;

import io.algopilot.agent.decision.LLMDecisionEngineService;
import io.algopilot.agent.decision.StructuredDecisionStore;
import io.algopilot.agent.decision.StructuredTradeDecision;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/decisions")
public class AgentDecisionController {
  private final DecisionJournalService service;
  private final AgentDecisionStore store;
  private final StructuredDecisionStore structuredStore;
  private final LLMDecisionEngineService decisionEngine;

  public AgentDecisionController(
      DecisionJournalService service,
      AgentDecisionStore store,
      StructuredDecisionStore structuredStore,
      LLMDecisionEngineService decisionEngine
  ) {
    this.service = service;
    this.store = store;
    this.structuredStore = structuredStore;
    this.decisionEngine = decisionEngine;
  }

  @GetMapping
  public List<AgentDecision> list(@RequestParam(defaultValue = "50") int limit) {
    return store.findRecent(limit);
  }

  @GetMapping("/structured")
  public List<StructuredTradeDecision> listStructured(@RequestParam(defaultValue = "50") int limit) {
    return structuredStore.findAllRecent(limit);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable UUID id) {
    Optional<StructuredTradeDecision> sOpt = structuredStore.findById(id);
    if (sOpt.isPresent()) {
      return ResponseEntity.ok(sOpt.get());
    }
    Optional<AgentDecision> aOpt = store.findById(id);
    return aOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/latest/{botId}")
  public ResponseEntity<StructuredTradeDecision> getLatest(@PathVariable UUID botId) {
    return structuredStore.findLatestByBotId(botId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/analyze/{botId}")
  public ResponseEntity<StructuredTradeDecision> analyze(@PathVariable UUID botId) {
    StructuredTradeDecision decision = decisionEngine.analyzeBot(botId);
    return ResponseEntity.ok(decision);
  }

  @PostMapping
  public ResponseEntity<AgentDecision> journal(@Valid @RequestBody StructuredDecisionRequest request) {
    AgentDecision decision = service.journal(request);
    return ResponseEntity.created(URI.create("/api/agent/decisions/" + decision.id())).body(decision);
  }

  @ExceptionHandler({DecisionValidationException.class, Exception.class})
  public ResponseEntity<?> invalid(Exception error) {
    return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
  }
}
