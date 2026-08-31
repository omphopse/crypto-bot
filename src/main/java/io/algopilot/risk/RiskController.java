package io.algopilot.risk;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
public class RiskController {
  private final RiskDecisionService riskDecisions;
  public RiskController(RiskDecisionService riskDecisions) { this.riskDecisions = riskDecisions; }
  @org.springframework.web.bind.annotation.GetMapping("/limits")
  public RiskLimits getLimits() {
    return RiskLimits.defaults();
  }

  @PostMapping("/evaluate")
  public ResponseEntity<RiskDecision> evaluate(@Valid @RequestBody RiskDecisionRequest request) {
    RiskDecision decision = riskDecisions.evaluate(request);
    return ResponseEntity.ok(decision);
  }
}
