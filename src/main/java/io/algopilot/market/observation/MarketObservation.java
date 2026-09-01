package io.algopilot.market.observation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketObservation(
    UUID id,
    String symbol,
    String provider,
    String environment,
    BigDecimal lastPrice,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal spread,
    BigDecimal volume,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    String timeframe,
    Instant marketTimestamp,
    Instant receivedAt,
    boolean isStale,
    long freshnessMs,
    String validationStatus
) {
  public static MarketObservation create(
      UUID id,
      String symbol,
      String provider,
      String environment,
      BigDecimal lastPrice,
      BigDecimal bid,
      BigDecimal ask,
      BigDecimal volume,
      BigDecimal openPrice,
      BigDecimal highPrice,
      BigDecimal lowPrice,
      BigDecimal closePrice,
      String timeframe,
      Instant marketTimestamp,
      Instant receivedAt,
      long staleThresholdMs
  ) {
    if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol cannot be empty");
    if (lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, openPrice, highPrice, lowPrice, closePrice, timeframe, marketTimestamp, receivedAt, "INVALID_PRICE");
    }
    if (volume != null && volume.compareTo(BigDecimal.ZERO) < 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, openPrice, highPrice, lowPrice, closePrice, timeframe, marketTimestamp, receivedAt, "INVALID_VOLUME");
    }
    if (bid != null && bid.compareTo(BigDecimal.ZERO) < 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, openPrice, highPrice, lowPrice, closePrice, timeframe, marketTimestamp, receivedAt, "INVALID_BID");
    }
    if (ask != null && ask.compareTo(BigDecimal.ZERO) < 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, openPrice, highPrice, lowPrice, closePrice, timeframe, marketTimestamp, receivedAt, "INVALID_ASK");
    }
    if (bid != null && ask != null && bid.compareTo(BigDecimal.ZERO) > 0 && ask.compareTo(BigDecimal.ZERO) > 0 && ask.compareTo(bid) < 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, openPrice, highPrice, lowPrice, closePrice, timeframe, marketTimestamp, receivedAt, "CROSSED_MARKET_BID_GREATER_THAN_ASK");
    }

    BigDecimal o = openPrice != null ? openPrice : lastPrice;
    BigDecimal h = highPrice != null ? highPrice : lastPrice;
    BigDecimal l = lowPrice != null ? lowPrice : lastPrice;
    BigDecimal c = closePrice != null ? closePrice : lastPrice;

    if (h.compareTo(o) < 0 || h.compareTo(c) < 0 || h.compareTo(l) < 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, o, h, l, c, timeframe, marketTimestamp, receivedAt, "INVALID_HIGH_LESS_THAN_OHLC");
    }
    if (l.compareTo(o) > 0 || l.compareTo(c) > 0 || l.compareTo(h) > 0) {
      return invalid(id, symbol, provider, environment, lastPrice, bid, ask, volume, o, h, l, c, timeframe, marketTimestamp, receivedAt, "INVALID_LOW_GREATER_THAN_OHLC");
    }

    long freshness = Math.max(0, receivedAt.toEpochMilli() - marketTimestamp.toEpochMilli());
    boolean stale = freshness > staleThresholdMs;
    BigDecimal spread = (ask != null && bid != null) ? ask.subtract(bid) : BigDecimal.ZERO;
    String status = stale ? "STALE" : "VALID";

    return new MarketObservation(
        id != null ? id : UUID.randomUUID(),
        symbol.toUpperCase(),
        provider != null ? provider : "ALPACA_PAPER",
        environment != null ? environment : "PAPER",
        lastPrice,
        bid,
        ask,
        spread,
        volume != null ? volume : BigDecimal.ZERO,
        o,
        h,
        l,
        c,
        timeframe != null ? timeframe : "1m",
        marketTimestamp,
        receivedAt,
        stale,
        freshness,
        status
    );
  }

  private static MarketObservation invalid(
      UUID id, String symbol, String provider, String environment,
      BigDecimal lastPrice, BigDecimal bid, BigDecimal ask, BigDecimal volume,
      BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
      String timeframe, Instant marketTimestamp, Instant receivedAt, String reason
  ) {
    long freshness = marketTimestamp != null ? Math.max(0, receivedAt.toEpochMilli() - marketTimestamp.toEpochMilli()) : 0;
    return new MarketObservation(
        id != null ? id : UUID.randomUUID(),
        symbol,
        provider,
        environment,
        lastPrice != null ? lastPrice : BigDecimal.ZERO,
        bid,
        ask,
        BigDecimal.ZERO,
        volume != null ? volume : BigDecimal.ZERO,
        open != null ? open : BigDecimal.ZERO,
        high != null ? high : BigDecimal.ZERO,
        low != null ? low : BigDecimal.ZERO,
        close != null ? close : BigDecimal.ZERO,
        timeframe != null ? timeframe : "1m",
        marketTimestamp != null ? marketTimestamp : receivedAt,
        receivedAt,
        true,
        freshness,
        "INVALID:" + reason
    );
  }
}
