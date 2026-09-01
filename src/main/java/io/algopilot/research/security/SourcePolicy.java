package io.algopilot.research.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SourcePolicy {
  public enum PolicyMode {
    ALLOWLIST,
    BLOCKLIST,
    UNRESTRICTED_WITH_SECURITY_FILTER
  }

  private volatile PolicyMode mode = PolicyMode.ALLOWLIST;
  private final Set<String> allowlist = ConcurrentHashMap.newKeySet();
  private final Set<String> blocklist = ConcurrentHashMap.newKeySet();

  public SourcePolicy() {
    // Default reputable financial & regulatory domains
    allowlist.addAll(Set.of(
        "sec.gov",
        "bloomberg.com",
        "reuters.com",
        "coindesk.com",
        "cointelegraph.com",
        "federalreserve.gov",
        "cnbc.com",
        "finance.yahoo.com",
        "marketwatch.com",
        "wsj.com",
        "ft.com"
    ));
  }

  public boolean isDomainAllowed(String domain) {
    if (domain == null || domain.isBlank()) return false;
    String cleanDomain = domain.toLowerCase().trim();

    if (blocklist.contains(cleanDomain) || blocklist.stream().anyMatch(b -> cleanDomain.endsWith("." + b))) {
      return false;
    }

    if (mode == PolicyMode.ALLOWLIST) {
      return allowlist.contains(cleanDomain) || allowlist.stream().anyMatch(a -> cleanDomain.endsWith("." + a));
    }

    return true;
  }

  public void addAllowedDomain(String domain) {
    if (domain != null && !domain.isBlank()) allowlist.add(domain.toLowerCase().trim());
  }

  public void addBlockedDomain(String domain) {
    if (domain != null && !domain.isBlank()) blocklist.add(domain.toLowerCase().trim());
  }

  public PolicyMode getMode() { return mode; }
  public void setMode(PolicyMode mode) { this.mode = mode; }
}
