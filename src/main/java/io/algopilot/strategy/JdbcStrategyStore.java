package io.algopilot.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStrategyStore implements StrategyStore {
  private final JdbcTemplate jdbc; private final ObjectMapper json;
  public JdbcStrategyStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
  @Override public Strategy saveStrategy(Strategy strategy) {
    jdbc.update("insert into strategies (id, name, status, created_at) values (?, ?, ?, ?)", strategy.id(), strategy.name(), strategy.status(), strategy.createdAt());
    return strategy;
  }
  @Override public StrategyVersion saveVersion(StrategyVersion version) {
    try {
      jdbc.update("insert into strategy_versions (id, strategy_id, version_number, definition, change_reason, created_at) values (?, ?, ?, cast(? as jsonb), ?, ?)", version.id(), version.strategyId(), version.versionNumber(), json.writeValueAsString(version.definition()), version.changeReason(), version.createdAt());
      return version;
    } catch (JsonProcessingException error) { throw new IllegalArgumentException("Strategy definition cannot be serialized", error); }
  }
  @Override public int latestVersionNumber(UUID strategyId) {
    Integer number = jdbc.queryForObject("select coalesce(max(version_number), 0) from strategy_versions where strategy_id = ?", Integer.class, strategyId);
    return number == null ? 0 : number;
  }
}
