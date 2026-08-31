package io.algopilot.order;

import io.algopilot.risk.RiskDecisionRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService service;
  private final OrderLifecycleService lifecycle;
  private final OrderStore orderStore;

  public OrderController(OrderService service, OrderLifecycleService lifecycle, OrderStore orderStore) {
    this.service = service;
    this.lifecycle = lifecycle;
    this.orderStore = orderStore;
  }

  @org.springframework.web.bind.annotation.GetMapping
  public java.util.List<OrderRecord> list(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit) {
    return orderStore.findAll(limit);
  }

  @org.springframework.web.bind.annotation.GetMapping("/{orderId}")
  public ResponseEntity<OrderRecord> get(@PathVariable UUID orderId) {
    return orderStore.findById(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping public ResponseEntity<OrderRecord> create(@Valid @RequestBody RiskDecisionRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); }
  @PostMapping("/{orderId}/status") public OrderRecord transition(@PathVariable UUID orderId, @Valid @RequestBody OrderTransitionRequest request) { return lifecycle.transition(orderId, request); }
  @ExceptionHandler(OrderRejectedException.class) ResponseEntity<?> rejected(OrderRejectedException error) { return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reasons", error.decision().reasons())); }
  @ExceptionHandler({OrderTransitionException.class, OrderNotFoundException.class}) ResponseEntity<?> lifecycleError(RuntimeException error) { return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage())); }
}
