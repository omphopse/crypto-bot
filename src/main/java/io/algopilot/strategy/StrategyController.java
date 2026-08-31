package io.algopilot.strategy;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
public class StrategyController {
  private final StrategyService service;
  private final StrategyStore store;

  public StrategyController(StrategyService service, StrategyStore store) {
    this.service = service;
    this.store = store;
  }

  @org.springframework.web.bind.annotation.GetMapping
  public java.util.List<Strategy> listStrategies() {
    return store.findAllStrategies();
  }

  @org.springframework.web.bind.annotation.GetMapping("/versions")
  public java.util.List<StrategyVersion> listVersions() {
    return store.findAllVersions();
  }

  @PostMapping public ResponseEntity<StrategyVersion> create(@Valid @RequestBody CreateStrategyRequest request) {
    StrategyVersion version = service.create(request); return ResponseEntity.created(URI.create("/api/strategies/" + version.strategyId() + "/versions/1")).body(version);
  }
  @PostMapping("/{strategyId}/versions") public ResponseEntity<StrategyVersion> version(@PathVariable UUID strategyId, @Valid @RequestBody CreateStrategyRequest request) {
    StrategyVersion version = service.createVersion(strategyId, request); return ResponseEntity.created(URI.create("/api/strategies/" + strategyId + "/versions/" + version.versionNumber())).body(version);
  }
}
