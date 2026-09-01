package io.algopilot.agent.decision;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AiCostLimiter {
  private final Clock clock;
  private final int maxRequestsPerMinute = 20;
  private final int maxRequestsPerDay = 500;
  private final BigDecimal maxDailyCostUsd = new BigDecimal("10.00");

  private final ConcurrentHashMap<Long, AtomicInteger> minuteRequests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<LocalDate, AtomicInteger> dayRequests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<LocalDate, BigDecimal> dayCosts = new ConcurrentHashMap<>();

  public AiCostLimiter() {
    this(Clock.systemUTC());
  }

  public AiCostLimiter(Clock clock) {
    this.clock = clock;
  }

  public synchronized boolean isAllowed() {
    Instant now = clock.instant();
    long currentMinute = now.getEpochSecond() / 60;
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

    int minReqs = minuteRequests.computeIfAbsent(currentMinute, k -> new AtomicInteger(0)).get();
    if (minReqs >= maxRequestsPerMinute) {
      return false;
    }

    int dReqs = dayRequests.computeIfAbsent(today, k -> new AtomicInteger(0)).get();
    if (dReqs >= maxRequestsPerDay) {
      return false;
    }

    BigDecimal cost = dayCosts.getOrDefault(today, BigDecimal.ZERO);
    if (cost.compareTo(maxDailyCostUsd) >= 0) {
      return false;
    }

    return true;
  }

  public synchronized boolean tryAcquire() {
    if (!isAllowed()) {
      return false;
    }
    Instant now = clock.instant();
    long currentMinute = now.getEpochSecond() / 60;
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

    minuteRequests.computeIfAbsent(currentMinute, k -> new AtomicInteger(0)).incrementAndGet();
    dayRequests.computeIfAbsent(today, k -> new AtomicInteger(0)).incrementAndGet();
    return true;
  }

  public synchronized void recordUsage(int inTokens, int outTokens, BigDecimal costUsd) {
    if (costUsd == null || costUsd.compareTo(BigDecimal.ZERO) <= 0) return;
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    dayCosts.compute(today, (k, current) -> (current == null ? BigDecimal.ZERO : current).add(costUsd));
  }
}
