package io.algopilot.market.observation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketDataStore {
  MarketObservation save(MarketObservation observation);
  Optional<MarketObservation> findLatest(String symbol);
  List<MarketObservation> findRecent(String symbol, int limit);
  List<MarketObservation> findAllLatest();
}
