package io.algopilot.portfolio.accounting;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/accounting")
public class PortfolioAccountingController {
  private final PortfolioAccountingService accountingService;

  public PortfolioAccountingController(PortfolioAccountingService accountingService) {
    this.accountingService = accountingService;
  }

  @GetMapping("/summary")
  public ResponseEntity<PortfolioSummary> getSummary() {
    return ResponseEntity.ok(accountingService.calculateSummary());
  }

  @GetMapping("/positions-mark")
  public ResponseEntity<List<PositionMark>> getMarkedPositions() {
    return ResponseEntity.ok(accountingService.getMarkedPositions());
  }
}
