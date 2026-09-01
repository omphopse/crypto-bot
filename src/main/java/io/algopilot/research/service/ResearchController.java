package io.algopilot.research.service;

import io.algopilot.research.model.ResearchDocument;
import io.algopilot.research.model.ResearchEvidence;
import io.algopilot.research.model.ResearchRequest;
import io.algopilot.research.model.ResearchResult;
import io.algopilot.research.model.ResearchSource;
import io.algopilot.research.provider.ResearchProvider;
import java.time.Instant;
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

@RestController("evidenceResearchController")
@RequestMapping("/api/research")
public class ResearchController {
  private final ResearchService researchService;
  private final ResearchStore store;

  public ResearchController(ResearchService researchService, ResearchStore store) {
    this.researchService = researchService;
    this.store = store;
  }

  public record CreateResearchHttpRequest(
      UUID sessionId,
      UUID botId,
      String asset,
      String topic,
      String query,
      Integer maxResults,
      Integer freshnessMinutes,
      List<ResearchProvider.RawPage> pages
  ) {}

  @PostMapping
  public ResponseEntity<ResearchResult> executeResearch(@RequestBody CreateResearchHttpRequest req) {
    ResearchRequest request = new ResearchRequest(
        UUID.randomUUID(),
        req.sessionId(),
        req.botId(),
        req.asset() != null ? req.asset().toUpperCase() : "GENERAL",
        req.topic() != null ? req.topic() : "MARKET_NEWS",
        req.query() != null ? req.query() : "",
        req.maxResults() != null ? req.maxResults() : 5,
        req.freshnessMinutes() != null ? req.freshnessMinutes() : 60,
        "PENDING",
        Instant.now(),
        null
    );

    ResearchResult result = researchService.executeResearch(request, req.pages());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResearchRequest> getRequest(@PathVariable UUID id) {
    return store.findRequestById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/documents")
  public ResponseEntity<List<ResearchDocument>> getDocuments(@PathVariable UUID id) {
    return ResponseEntity.ok(store.findDocumentsByRequestId(id));
  }

  @GetMapping("/{id}/evidence")
  public ResponseEntity<List<ResearchEvidence>> getEvidence(@PathVariable UUID id) {
    return ResponseEntity.ok(store.findEvidenceByRequestId(id));
  }

  @GetMapping("/sources")
  public ResponseEntity<List<ResearchSource>> getAllSources() {
    return ResponseEntity.ok(store.findAllSources());
  }

  @GetMapping("/evidence")
  public ResponseEntity<List<ResearchEvidence>> getEvidenceByAsset(
      @RequestParam String asset,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(store.findEvidenceByAsset(asset, limit));
  }
}
