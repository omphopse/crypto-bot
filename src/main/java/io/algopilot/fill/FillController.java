package io.algopilot.fill;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fills")
public class FillController {
  private final FillIngestionService ingestionService;
  private final FillStore fillStore;

  public FillController(FillIngestionService ingestionService, FillStore fillStore) {
    this.ingestionService = ingestionService;
    this.fillStore = fillStore;
  }

  @PostMapping
  public ResponseEntity<Fill> ingest(@RequestBody FillReport report) {
    return ResponseEntity.ok(ingestionService.ingest(report));
  }

  @GetMapping
  public List<Fill> list() {
    return fillStore.findAll();
  }
}
