package io.algopilot.market.scanner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketScanStore {
  ScanResult save(ScanResult result);
  List<ScanResult> findRecent(int limit);
  List<ScanResult> findRecentBySymbol(String symbol, int limit);
  Optional<ScanResult> findById(UUID id);
}
