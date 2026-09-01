package io.algopilot.market.observation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMarketDataStore implements MarketDataStore {
  private final JdbcTemplate jdbc;

  public JdbcMarketDataStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public MarketObservation save(MarketObservation o) {
    jdbc.update(
        "INSERT INTO market_observations (id, symbol, provider, environment, last_price, bid, ask, spread, " +
        "volume, open_price, high_price, low_price, close_price, timeframe, market_timestamp, received_at, is_stale, freshness_ms, validation_status) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        o.id(), o.symbol(), o.provider(), o.environment(), o.lastPrice(), o.bid(), o.ask(), o.spread(),
        o.volume(), o.openPrice(), o.highPrice(), o.lowPrice(), o.closePrice(), o.timeframe(),
        Timestamp.from(o.marketTimestamp()), Timestamp.from(o.receivedAt()), o.isStale(), o.freshnessMs(), o.validationStatus()
    );
    return o;
  }

  @Override
  public Optional<MarketObservation> findLatest(String symbol) {
    return jdbc.query(
        "SELECT * FROM market_observations WHERE symbol = ? ORDER BY market_timestamp DESC LIMIT 1",
        this::mapRow, symbol.toUpperCase()
    ).stream().findFirst();
  }

  @Override
  public List<MarketObservation> findRecent(String symbol, int limit) {
    return jdbc.query(
        "SELECT * FROM market_observations WHERE symbol = ? ORDER BY market_timestamp DESC LIMIT ?",
        this::mapRow, symbol.toUpperCase(), Math.max(1, limit)
    );
  }

  @Override
  public List<MarketObservation> findAllLatest() {
    return jdbc.query(
        "SELECT DISTINCT ON (symbol) * FROM market_observations ORDER BY symbol, market_timestamp DESC",
        this::mapRow
    );
  }

  private MarketObservation mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new MarketObservation(
        rs.getObject("id", UUID.class),
        rs.getString("symbol"),
        rs.getString("provider"),
        rs.getString("environment"),
        rs.getBigDecimal("last_price"),
        rs.getBigDecimal("bid"),
        rs.getBigDecimal("ask"),
        rs.getBigDecimal("spread"),
        rs.getBigDecimal("volume"),
        rs.getBigDecimal("open_price"),
        rs.getBigDecimal("high_price"),
        rs.getBigDecimal("low_price"),
        rs.getBigDecimal("close_price"),
        rs.getString("timeframe"),
        rs.getTimestamp("market_timestamp").toInstant(),
        rs.getTimestamp("received_at").toInstant(),
        rs.getBoolean("is_stale"),
        rs.getLong("freshness_ms"),
        rs.getString("validation_status")
    );
  }
}
