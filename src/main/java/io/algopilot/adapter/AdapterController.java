package io.algopilot.adapter;

import io.algopilot.order.OrderNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execution")
public class AdapterController {
  private final ExecutionGateway gateway;
  private final List<BrokerOrderAdapter> adapters;

  public AdapterController(ExecutionGateway gateway, List<BrokerOrderAdapter> adapters) {
    this.gateway = gateway;
    this.adapters = adapters;
  }

  @PostMapping("/dispatch/{orderId}")
  public ResponseEntity<OrderSubmissionResult> dispatch(@PathVariable UUID orderId) {
    OrderSubmissionResult result = gateway.dispatch(orderId);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/cancel/{orderId}")
  public ResponseEntity<OrderCancellationResult> cancel(
      @PathVariable UUID orderId,
      @RequestParam(required = false) String exchangeOrderId) {
    OrderCancellationResult result = gateway.cancel(orderId, exchangeOrderId);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/adapters")
  public List<Map<String, String>> listAdapters() {
    return adapters.stream()
        .map(a -> Map.of(
            "broker", a.broker().name(),
            "mode", a.supportedMode().name(),
            "status", "ACTIVE"
        ))
        .toList();
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleAdapterError(Exception error) {
    return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED", "reason", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
  }
}
