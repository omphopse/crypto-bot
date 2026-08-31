package io.algopilot.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.audit.AuditEventWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrategyServiceTest {
  @Test void versions_are_created_sequentially_without_mutating_the_original_definition() throws Exception {
    MemoryStore store = new MemoryStore(); StrategyService service = new StrategyService(store, mock(AuditEventWriter.class)); ObjectMapper json = new ObjectMapper();
    StrategyVersion first = service.create(new CreateStrategyRequest("BTC momentum", json.readTree("{\"rsi\":50}"), "initial"));
    StrategyVersion second = service.createVersion(first.strategyId(), new CreateStrategyRequest("BTC momentum", json.readTree("{\"rsi\":55}"), "raise threshold"));
    assertThat(first.versionNumber()).isEqualTo(1); assertThat(second.versionNumber()).isEqualTo(2);
    assertThat(first.definition().get("rsi").asInt()).isEqualTo(50); assertThat(second.definition().get("rsi").asInt()).isEqualTo(55);
  }
  private static final class MemoryStore implements StrategyStore {
    final HashMap<UUID, Integer> current = new HashMap<>();
    final ArrayList<StrategyVersion> versions = new ArrayList<>();
    public Strategy saveStrategy(Strategy strategy) { return strategy; }
    public StrategyVersion saveVersion(StrategyVersion version) { versions.add(version); current.put(version.strategyId(), version.versionNumber()); return version; }
    public java.util.Optional<StrategyVersion> findVersionById(UUID id) { return versions.stream().filter(v -> v.id().equals(id)).findFirst(); }
    public int latestVersionNumber(UUID id) { return current.getOrDefault(id, 0); }
  }
}
