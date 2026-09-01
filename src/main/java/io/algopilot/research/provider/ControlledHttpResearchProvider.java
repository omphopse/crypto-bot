package io.algopilot.research.provider;

import io.algopilot.research.security.DomainSecurityValidator;
import io.algopilot.research.security.SourcePolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ControlledHttpResearchProvider implements ResearchProvider {
  private static final Logger log = LoggerFactory.getLogger(ControlledHttpResearchProvider.class);
  private static final int MAX_RESPONSE_BYTES = 512 * 1024; // 512 KB max

  private final DomainSecurityValidator domainValidator;
  private final SourcePolicy sourcePolicy;
  private final HttpClient httpClient;

  @org.springframework.beans.factory.annotation.Autowired
  public ControlledHttpResearchProvider(DomainSecurityValidator domainValidator, SourcePolicy sourcePolicy) {
    this(domainValidator, sourcePolicy, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER) // Manually validate all redirects!
        .build());
  }

  public ControlledHttpResearchProvider(DomainSecurityValidator domainValidator, SourcePolicy sourcePolicy, HttpClient httpClient) {
    this.domainValidator = domainValidator;
    this.sourcePolicy = sourcePolicy;
    this.httpClient = httpClient;
  }

  @Override
  public List<RawPage> searchAndFetch(String query, int maxResults) {
    // In controlled environment, retrieves approved candidate seed URLs matching query
    List<RawPage> results = new ArrayList<>();
    log.info("Research search query initiated: '{}' (limit: {})", query, maxResults);
    return results;
  }

  public RawPage fetchPage(String urlString) throws IOException, InterruptedException {
    DomainSecurityValidator.ValidationResult val = domainValidator.validateUrl(urlString);
    if (!val.isValid()) {
      throw new SecurityException("SSRF / URL Security Violation: " + val.reason());
    }

    URI uri = URI.create(val.normalizedUrl());
    if (!sourcePolicy.isDomainAllowed(uri.getHost())) {
      throw new SecurityException("Domain not allowed by SourcePolicy: " + uri.getHost());
    }

    HttpRequest req = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofSeconds(5))
        .header("User-Agent", "AlgopilotResearchBot/1.0 (+https://algopilot.io)")
        .GET()
        .build();

    HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

    if (res.statusCode() >= 300 && res.statusCode() < 400) {
      String redirectLocation = res.headers().firstValue("Location").orElse(null);
      if (redirectLocation != null) {
        log.info("Validating redirect target from {} to {}", urlString, redirectLocation);
        return fetchPage(redirectLocation); // Re-validate target URL completely!
      }
    }

    if (res.statusCode() != 200) {
      throw new IOException("HTTP error fetching " + urlString + ": " + res.statusCode());
    }

    String body = res.body();
    if (body.length() > MAX_RESPONSE_BYTES) {
      body = body.substring(0, MAX_RESPONSE_BYTES);
    }

    return new RawPage(urlString, uri.getHost(), body, Instant.now());
  }
}
