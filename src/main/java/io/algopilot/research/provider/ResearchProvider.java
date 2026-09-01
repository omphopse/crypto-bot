package io.algopilot.research.provider;

import java.time.Instant;
import java.util.List;

public interface ResearchProvider {
  record RawPage(String url, String title, String rawContent, Instant publishedAt) {}

  List<RawPage> searchAndFetch(String query, int maxResults);
}
