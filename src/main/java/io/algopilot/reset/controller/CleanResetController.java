package io.algopilot.reset.controller;

import io.algopilot.reset.model.PreflightStatusResponse;
import io.algopilot.reset.service.CleanResetService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reset")
public class CleanResetController {
  private final CleanResetService resetService;

  public CleanResetController(CleanResetService resetService) {
    this.resetService = resetService;
  }

  @GetMapping("/preflight")
  public ResponseEntity<PreflightStatusResponse> getPreflightStatus() {
    return ResponseEntity.ok(resetService.getPreflightStatus());
  }

  @PostMapping("/confirm")
  public ResponseEntity<Map<String, Object>> confirmReset(
      @RequestParam(required = false, defaultValue = "") String confirmationString,
      @RequestParam(required = false, defaultValue = "OPERATOR") String operator,
      @RequestBody(required = false) Map<String, String> body
  ) {
    String conf = (confirmationString != null && !confirmationString.isBlank())
        ? confirmationString
        : (body != null ? body.getOrDefault("confirmationString", "") : "");
    String op = (operator != null && !operator.isBlank() && !"OPERATOR".equals(operator))
        ? operator
        : (body != null ? body.getOrDefault("operator", "OPERATOR") : "OPERATOR");

    Map<String, Object> result = resetService.confirmReset(conf, op);
    return ResponseEntity.ok(result);
  }
}
