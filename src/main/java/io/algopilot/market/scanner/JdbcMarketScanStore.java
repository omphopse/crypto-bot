package io.algopilot.market.scanner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.algopilot.market.observation.MarketObservation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMarketScanStore implements MarketScanStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public JdbcMarketScanStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public ScanResult save(ScanResult r) {
    String trigJson = "[]";
    String indJson = "{}";
    String mktJson = "{}";
    try {
      if (r.triggerConditions() != null) trigJson = json.writeValueAsString(r.triggerConditions());
      if (r.indicatorSnapshot() != null) indJson = json.writeValueAsString(r.indicatorSnapshot());
      if (r.marketObservation() != null) mktJson = json.writeValueAsString(r.marketObservation());
    } catch (JsonProcessingException ignored) {}

    jdbc.update(
        "INSERT INTO market_scan_results (id, session_id, bot_id, symbol, timeframe, provider, candidate_type, " +
        "trigger_conditions, indicator_snapshot, market_snapshot, confidence_score, reason, status, timestamp) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?)",
        r.id(), r.sessionId(), r.botId(), r.symbol(), r.timeframe(), r.provider(), r.candidateType().name(),
        trigJson, indJson, mktJson, r.confidenceScore(), r.reason(), r.status(), Timestamp.from(r.timestamp())
    );
    return r;
  }

  @Override
  public List<ScanResult> findRecent(int limit) {
    return jdbc.query("SELECT * FROM market_scan_results ORDER BY timestamp DESC LIMIT ?", this::mapRow, Math.max(1, limit));
  }

  @Override
  public List<ScanResult> findRecentBySymbol(String symbol, int limit) {
    return jdbc.query(
        "SELECT * FROM market_scan_results WHERE symbol = ? ORDER BY timestamp DESC LIMIT ?",
        this::mapRow, symbol.toUpperCase(), Math.max(1, limit)
    );
  }

  @Override
  public Optional<ScanResult> findById(UUID id) {
    return jdbc.query("SELECT * FROM market_scan_results WHERE id = ?", this::mapRow, id).stream().findFirst();
  }

  private ScanResult mapRow(ResultSet rs, int rowNum) throws SQLException {
    try {
      List<String> triggers = json.readValue(rs.getString("trigger_conditions"), new TypeReference<>() {});
      IndicatorSnapshot indicatorSnapshot = json.readValue(rs.getString("indicator_snapshot"), IndicatorSnapshot.class);
      MarketObservation marketObservation = json.readValue(rs.getString("market_snapshot"), MarketObservation.class);

      return new ScanResult(
          rs.getObject("id", UUID.class),
          rs.getObject("session_id", UUID.class),
          rs.getObject("bot_id", UUID.class),
          rs.getString("symbol"),
          rs.getString("timeframe"),
          rs.getString("provider"),
          CandidateType.valueOf(rs.getString("candidate_type")),
          triggers,
          indicatorSnapshot,
          marketObservation,
          rs.getBigDecimal("confidence_score"),
          rs.getString("reason"),
          rs.getString("status"),
          rs.getTimestamp("timestamp").toInstant()
      );
    } catch (JsonProcessingException e) {
      throw new SQLException("Failed to deserialize scan result json", e);
    }
  }
}
