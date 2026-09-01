package io.algopilot.research.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DomainSecurityValidator {
  private static final Logger log = LoggerFactory.getLogger(DomainSecurityValidator.class);

  private static final Set<String> BLOCKED_HOSTS = Set.of(
      "localhost", "127.0.0.1", "::1", "metadata.google.internal", "instance-data"
  );

  public record ValidationResult(boolean isValid, String reason, String normalizedUrl) {
    public static ValidationResult valid(String url) { return new ValidationResult(true, "OK", url); }
    public static ValidationResult invalid(String reason) { return new ValidationResult(false, reason, null); }
  }

  public ValidationResult validateUrl(String urlString) {
    if (urlString == null || urlString.isBlank()) {
      return ValidationResult.invalid("Empty URL");
    }

    URI uri;
    try {
      uri = URI.create(urlString.trim());
    } catch (Exception e) {
      return ValidationResult.invalid("Malformed URI: " + e.getMessage());
    }

    String scheme = uri.getScheme();
    if (scheme == null || !scheme.equalsIgnoreCase("https")) {
      return ValidationResult.invalid("Insecure or unsupported scheme (strictly HTTPS required): " + scheme);
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return ValidationResult.invalid("Missing host");
    }
    host = host.toLowerCase();

    if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".internal")) {
      return ValidationResult.invalid("Blocked local or internal host: " + host);
    }

    int port = uri.getPort();
    if (port != -1 && port != 443) {
      return ValidationResult.invalid("Non-standard HTTPS port rejected for SSRF protection: " + port);
    }

    // SSRF IP Validation
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress addr : addresses) {
        if (isPrivateOrLocalIp(addr)) {
          return ValidationResult.invalid("Host resolves to private/link-local/loopback IP address: " + addr.getHostAddress());
        }
      }
    } catch (UnknownHostException e) {
      log.debug("DNS lookup failed during security check for {}", host);
    }

    return ValidationResult.valid(uri.normalize().toString());
  }

  public boolean isPrivateOrLocalIp(InetAddress addr) {
    if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
      return true;
    }
    byte[] b = addr.getAddress();
    if (b.length == 4) {
      int first = b[0] & 0xFF;
      int second = b[1] & 0xFF;
      if (first == 10) return true; // 10.0.0.0/8
      if (first == 172 && (second >= 16 && second <= 31)) return true; // 172.16.0.0/12
      if (first == 192 && second == 168) return true; // 192.168.0.0/16
      if (first == 169 && second == 254) return true; // 169.254.0.0/16 (Link local / AWS/GCP Metadata 169.254.169.254)
      if (first == 127) return true; // 127.0.0.0/8
      if (first == 0) return true; // 0.0.0.0/8
    }
    return false;
  }
}
