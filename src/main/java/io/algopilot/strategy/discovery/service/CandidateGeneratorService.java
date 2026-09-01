package io.algopilot.strategy.discovery.service;

import io.algopilot.strategy.discovery.model.*;
import io.algopilot.strategy.discovery.persistence.CandidateStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CandidateGeneratorService {
  private final CandidateStore candidateStore;
  private final Clock clock;

  public CandidateGeneratorService(CandidateStore candidateStore, Clock clock) {
    this.candidateStore = candidateStore;
    this.clock = clock;
  }

  public List<StrategyCandidate> generateGridCandidates(
      UUID baseStrategyId,
      String symbol,
      String timeframe,
      StrategyFamily family
  ) {
    Instant now = clock.instant();
    List<StrategyCandidate> generated = new ArrayList<>();

    int[] fastEmas = {5, 9, 12, 20};
    int[] slowEmas = {21, 30, 50};
    int[] rsiThresholds = {40, 45, 50, 55};
    double[] stops = {0.002, 0.003, 0.005};
    double[] takeProfits = {0.003, 0.005, 0.008};

    for (int fast : fastEmas) {
      for (int slow : slowEmas) {
        if (fast >= slow) continue;
        for (int rsi : rsiThresholds) {
          for (double stop : stops) {
            for (double tp : takeProfits) {
              Map<String, String> params = new LinkedHashMap<>();
              params.put("fastEma", String.valueOf(fast));
              params.put("slowEma", String.valueOf(slow));
              params.put("rsiThreshold", String.valueOf(rsi));
              params.put("stopLossPct", String.valueOf(stop));
              params.put("takeProfitPct", String.valueOf(tp));

              String fingerprint = computeFingerprint(family.name(), symbol, timeframe, params);
              Optional<StrategyCandidate> existing = candidateStore.findCandidateByFingerprint(fingerprint);
              if (existing.isPresent()) {
                continue;
              }

              String name = String.format("%s-%s-F%d-S%d-R%d", family.name(), symbol, fast, slow, rsi);
              StrategyCandidate cand = new StrategyCandidate(
                  UUID.randomUUID(),
                  fingerprint,
                  baseStrategyId != null ? baseStrategyId : UUID.randomUUID(),
                  name,
                  family,
                  symbol,
                  timeframe,
                  params,
                  GenerationMethod.PARAMETER_SWEEP,
                  CandidateStatus.GENERATED,
                  null,
                  null,
                  null,
                  null,
                  null,
                  now
              );
              candidateStore.saveCandidate(cand);
              generated.add(cand);
            }
          }
        }
      }
    }
    return generated;
  }

  public String computeFingerprint(String family, String symbol, String timeframe, Map<String, String> params) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      String raw = family + ":" + symbol + ":" + timeframe + ":" + new TreeMap<>(params);
      byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new RuntimeException("Fingerprint hash failed", e);
    }
  }
}
