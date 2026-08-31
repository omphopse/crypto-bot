package io.algopilot.bot;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bots")
public class BotController {
  private final BotService service;
  private final BotControlService controls;
  private final BotStore botStore;

  public BotController(BotService service, BotControlService controls, BotStore botStore) {
    this.service = service;
    this.controls = controls;
    this.botStore = botStore;
  }

  @org.springframework.web.bind.annotation.GetMapping
  public java.util.List<Bot> list() {
    return botStore.findAll();
  }

  @org.springframework.web.bind.annotation.GetMapping("/{id}")
  public ResponseEntity<Bot> get(@PathVariable java.util.UUID id) {
    return botStore.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping public ResponseEntity<Bot> deploy(@Valid @RequestBody DeployBotRequest request) { Bot bot = service.deploy(request); return ResponseEntity.created(URI.create("/api/bots/" + bot.id())).body(bot); }
  @PostMapping("/{id}/pause") public Bot pause(@PathVariable java.util.UUID id) { return controls.pause(id); }
  @PostMapping("/{id}/stop") public Bot stop(@PathVariable java.util.UUID id) { return controls.stop(id); }
  @PostMapping("/{id}/emergency-stop") public Bot emergencyStop(@PathVariable java.util.UUID id) { return controls.emergencyStop(id); }
  @PostMapping("/{id}/resume") public Bot resume(@PathVariable java.util.UUID id) { return controls.resume(id); }
  @ExceptionHandler(BotDeploymentException.class) ResponseEntity<?> blocked(BotDeploymentException error) { return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage())); }
  @ExceptionHandler({BotControlException.class, BotNotFoundException.class}) ResponseEntity<?> controlBlocked(RuntimeException error) { return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage())); }
}
