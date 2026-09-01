package io.algopilot.research.security;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ResearchRateLimiter {
  private final int maxPerMinute;
  private final AtomicInteger currentMinuteCount = new AtomicInteger(0);
  private volatile long lastMinuteWindow = System.currentTimeMillis() / 60_000L;

  @org.springframework.beans.factory.annotation.Autowired
  public ResearchRateLimiter() {
    this(30); // Default 30 requests per minute
  }

  public ResearchRateLimiter(int maxPerMinute) {
    this.maxPerMinute = maxPerMinute;
  }

  public synchronized boolean tryAcquire() {
    long currentWindow = System.currentTimeMillis() / 60_000L;
    if (currentWindow != lastMinuteWindow) {
      lastMinuteWindow = currentWindow;
      currentMinuteCount.set(0);
    }
    if (currentMinuteCount.get() >= maxPerMinute) {
      return false;
    }
    currentMinuteCount.incrementAndGet();
    return true;
  }
}
