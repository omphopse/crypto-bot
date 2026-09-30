package io.algopilot.market.scanner;

import io.algopilot.audit.AuditEventWriter;
import io.algopilot.backtest.engine.Indicators;
import io.algopilot.backtest.model.Candle;
import io.algopilot.market.observation.MarketObservation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketScanner {
  private static final Logger log = LoggerFactory.getLogger(MarketScanner.class);

  private final MarketScanStore scanStore;
  private final AuditEventWriter audit;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public MarketScanner(
      MarketScanStore scanStore,
      AuditEventWriter audit,
      @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
    this.scanStore = scanStore;
    this.audit = audit;
    this.clock = clock != null ? clock : Clock.systemUTC();
  }

  public MarketScanner(MarketScanStore scanStore, AuditEventWriter audit) {
    this(scanStore, audit, Clock.systemUTC());
  }

  public ScanResult scan(UUID sessionId, UUID botId, List<Candle> candles, MarketObservation currentObservation) {
    return scan(sessionId, botId, candles, currentObservation, 9, 21, 14);
  }

  public ScanResult scan(
      UUID sessionId,
      UUID botId,
      List<Candle> candles,
      MarketObservation currentObservation,
      int fastEmaPeriod,
      int slowEmaPeriod,
      int rsiPeriod
  ) {
    Instant now = clock.instant();
    String symbol = currentObservation != null ? currentObservation.symbol() : (candles != null && !candles.isEmpty() ? candles.get(0).symbol() : "UNKNOWN");
    String timeframe = currentObservation != null ? currentObservation.timeframe() : "1m";
    String provider = currentObservation != null ? currentObservation.provider() : "UNKNOWN";

    if (currentObservation != null && currentObservation.isStale()) {
      ScanResult staleResult = new ScanResult(
          UUID.randomUUID(), sessionId, botId, symbol, timeframe, provider,
          CandidateType.NO_CANDIDATE, List.of("MARKET_DATA_STALE"),
          null, currentObservation, BigDecimal.ZERO, "Market data is stale; scan aborted", "SKIPPED_STALE_DATA", now
      );
      scanStore.save(staleResult);
      return staleResult;
    }

    int minBars = Math.max(20, Math.max(fastEmaPeriod, slowEmaPeriod));
    if (candles == null || candles.size() < minBars) {
      IndicatorSnapshot coldSnap = new IndicatorSnapshot(
          symbol, timeframe, now, null, null, null, null, null, null, null, null, null, null, null, null,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
      );
      ScanResult coldResult = new ScanResult(
          UUID.randomUUID(), sessionId, botId, symbol, timeframe, provider,
          CandidateType.NO_CANDIDATE, List.of("INSUFFICIENT_BAR_HISTORY"),
          coldSnap, currentObservation, BigDecimal.ZERO, "Insufficient candle history for indicator warm-up (<" + minBars + " bars)", "NO_CANDIDATE", now
      );
      scanStore.save(coldResult);
      return coldResult;
    }

    List<BigDecimal> closes = candles.stream().map(Candle::close).toList();
    List<BigDecimal> volumes = candles.stream().map(Candle::volume).toList();

    List<BigDecimal> emaFast = Indicators.ema(closes, fastEmaPeriod);
    List<BigDecimal> emaSlow = Indicators.ema(closes, slowEmaPeriod);
    List<BigDecimal> sma50 = Indicators.sma(closes, 50);
    List<BigDecimal> sma200 = Indicators.sma(closes, 200);
    List<BigDecimal> rsiList = Indicators.rsi(closes, rsiPeriod);
    Indicators.MacdResult macdRes = Indicators.macd(closes, 12, 26, 9);
    List<BigDecimal> atr14 = Indicators.atr(candles, 14);
    Indicators.BollingerBands bb = Indicators.bollingerBands(closes, 20, 2.0);
    List<BigDecimal> avgVol20 = Indicators.sma(volumes, 20);

    int lastIdx = candles.size() - 1;
    BigDecimal curClose = closes.get(lastIdx);
    BigDecimal curVol = volumes.get(lastIdx);
    BigDecimal curEmaFast = emaFast.get(lastIdx);
    BigDecimal curEmaSlow = emaSlow.get(lastIdx);
    BigDecimal curSma50 = sma50.size() > lastIdx ? sma50.get(lastIdx) : null;
    BigDecimal curSma200 = sma200.size() > lastIdx ? sma200.get(lastIdx) : null;
    BigDecimal curRsi = rsiList.get(lastIdx);
    BigDecimal curMacd = macdRes.macd().size() > lastIdx ? macdRes.macd().get(lastIdx) : null;
    BigDecimal curMacdSig = macdRes.signal().size() > lastIdx ? macdRes.signal().get(lastIdx) : null;
    BigDecimal curMacdHist = macdRes.histogram().size() > lastIdx ? macdRes.histogram().get(lastIdx) : null;
    BigDecimal curAtr = atr14.get(lastIdx);
    BigDecimal curBbUpper = bb.upper().get(lastIdx);
    BigDecimal curBbMiddle = bb.middle().get(lastIdx);
    BigDecimal curBbLower = bb.lower().get(lastIdx);
    BigDecimal curAvgVol = avgVol20.get(lastIdx);

    BigDecimal prevClose = lastIdx > 0 ? closes.get(lastIdx - 1) : curClose;
    BigDecimal priceChangePct = prevClose.compareTo(BigDecimal.ZERO) > 0
        ? curClose.subtract(prevClose).multiply(new BigDecimal("100")).divide(prevClose, 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    BigDecimal volatility = curAtr != null && curClose.compareTo(BigDecimal.ZERO) > 0
        ? curAtr.divide(curClose, 4, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    IndicatorSnapshot snapshot = new IndicatorSnapshot(
        symbol, timeframe, now,
        curEmaFast, curEmaSlow, curSma50, curSma200, curRsi,
        curMacd, curMacdSig, curMacdHist, curAtr,
        curBbUpper, curBbMiddle, curBbLower,
        curAvgVol, curVol, priceChangePct, volatility, true
    );

    // Evaluate Deterministic Scanner Trigger Conditions
    List<String> triggers = new ArrayList<>();
    CandidateType candidateType = CandidateType.NO_CANDIDATE;
    BigDecimal score = BigDecimal.ZERO;
    String reason = "No significant technical anomaly detected";

    // 1. Breakout (20-bar high breakout with volume expansion)
    BigDecimal maxRecentHigh = BigDecimal.ZERO;
    for (int i = Math.max(0, lastIdx - 20); i < lastIdx; i++) {
      maxRecentHigh = maxRecentHigh.max(candles.get(i).high());
    }
    if (curClose.compareTo(maxRecentHigh) > 0 && curAvgVol != null && curVol.compareTo(curAvgVol.multiply(new BigDecimal("1.3"))) > 0) {
      triggers.add("20_BAR_HIGH_BREAKOUT");
      triggers.add("VOLUME_ABOVE_AVERAGE");
      candidateType = CandidateType.BREAKOUT;
      score = new BigDecimal("0.88");
      reason = "Price broke out above 20-bar high (" + curClose + " > " + maxRecentHigh + ") with volume expansion";
    }

    // 2. Volume Anomaly (2.5x volume without breakout)
    else if (curAvgVol != null && curAvgVol.compareTo(BigDecimal.ZERO) > 0 && curVol.compareTo(curAvgVol.multiply(new BigDecimal("2.5"))) > 0) {
      triggers.add("VOLUME_SPIKE_2.5X");
      candidateType = CandidateType.VOLUME_ANOMALY;
      score = new BigDecimal("0.75");
      reason = "Volume anomaly detected: " + curVol + " vs 20-bar avg " + curAvgVol;
    }

    // 3. Momentum Condition (Fast EMA > Slow EMA and RSI > 50 and Price >= Fast EMA)
    else if (curEmaFast != null && curEmaSlow != null && curEmaFast.compareTo(curEmaSlow) > 0 && curRsi != null && curRsi.compareTo(new BigDecimal("50.00")) > 0 && curClose.compareTo(curEmaFast) >= 0) {
      triggers.add("EMA" + fastEmaPeriod + "_ABOVE_EMA" + slowEmaPeriod);
      triggers.add("RSI_BULLISH_>50");
      triggers.add("PRICE_ABOVE_EMA" + fastEmaPeriod);
      candidateType = CandidateType.MOMENTUM;
      score = new BigDecimal("0.85");
      reason = "Bullish momentum structure confirmed across EMA" + fastEmaPeriod + "/" + slowEmaPeriod + ", RSI, and price action";
    }

    // 4. Oversold / Mean Reversion
    else if (curRsi != null && curRsi.compareTo(new BigDecimal("30.00")) < 0) {
      triggers.add("RSI_OVERSOLD_<30");
      candidateType = CandidateType.OVERSOLD;
      score = new BigDecimal("0.80");
      reason = "RSI oversold condition detected at " + curRsi;
    } else if (curBbLower != null && curClose.compareTo(curBbLower) < 0) {
      triggers.add("PRICE_BELOW_LOWER_BOLLINGER");
      candidateType = CandidateType.MEAN_REVERSION;
      score = new BigDecimal("0.78");
      reason = "Price traded below lower Bollinger Band (" + curClose + " < " + curBbLower + ")";
    }

    // 5. Overbought
    else if (curRsi != null && curRsi.compareTo(new BigDecimal("70.00")) > 0) {
      triggers.add("RSI_OVERBOUGHT_>70");
      candidateType = CandidateType.OVERBOUGHT;
      score = new BigDecimal("0.75");
      reason = "RSI overbought condition detected at " + curRsi;
    }

    String status = candidateType != CandidateType.NO_CANDIDATE ? "CANDIDATE_DETECTED" : "NO_CANDIDATE";

    ScanResult result = new ScanResult(
        UUID.randomUUID(), sessionId, botId, symbol, timeframe, provider,
        candidateType, triggers, snapshot, currentObservation, score, reason, status, now
    );

    scanStore.save(result);

    if (candidateType != CandidateType.NO_CANDIDATE) {
      audit.record("SCANNER", symbol, "SCAN_CANDIDATE_DETECTED", "SCAN_RESULT", result.id().toString(),
          Map.of("candidateType", candidateType.name(), "score", score.toString(), "reason", reason));
      log.info("Scanner candidate detected for {}: type={}, score={}, triggers={}", symbol, candidateType, score, triggers);
    } else {
      audit.record("SCANNER", symbol, "SCAN_COMPLETED", "SCAN_RESULT", result.id().toString(),
          Map.of("status", "NO_CANDIDATE"));
    }

    return result;
  }
}
