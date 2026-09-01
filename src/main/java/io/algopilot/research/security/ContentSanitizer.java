package io.algopilot.research.security;

import org.springframework.stereotype.Component;

@Component
public class ContentSanitizer {
  private static final int MAX_DOCUMENT_CHARS = 100_000; // 100k chars max per document

  public String sanitize(String rawContent) {
    if (rawContent == null || rawContent.isBlank()) return "";

    // 1. Strip script and style blocks
    String cleaned = rawContent.replaceAll("(?is)<script.*?</script>", " ");
    cleaned = cleaned.replaceAll("(?is)<style.*?</style>", " ");

    // 2. Strip HTML tags
    cleaned = cleaned.replaceAll("<[^>]*>", " ");

    // 3. Decode HTML entities
    cleaned = cleaned.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ");

    // 4. Collapse whitespace
    cleaned = cleaned.replaceAll("[\\t\\n\\r]+", " ");
    cleaned = cleaned.replaceAll(" {2,}", " ").trim();

    // 5. Enforce max document size
    if (cleaned.length() > MAX_DOCUMENT_CHARS) {
      cleaned = cleaned.substring(0, MAX_DOCUMENT_CHARS);
    }

    return cleaned;
  }
}
