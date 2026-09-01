package io.algopilot.research;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.research.security.ContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentSanitizerTest {
  private ContentSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new ContentSanitizer();
  }

  @Test
  void testHtmlAndScriptsStripped() {
    String html = "<html><head><script>alert(1)</script><style>body{color:red}</style></head><body><h1>Federal Reserve Announcement</h1><p>Interest rates remain unchanged &amp; steady.</p></body></html>";
    String sanitized = sanitizer.sanitize(html);

    assertThat(sanitized).doesNotContain("<script>");
    assertThat(sanitized).doesNotContain("alert(1)");
    assertThat(sanitized).doesNotContain("<style>");
    assertThat(sanitized).contains("Federal Reserve Announcement");
    assertThat(sanitized).contains("Interest rates remain unchanged & steady.");
  }

  @Test
  void testWhitespaceCollapsed() {
    String messy = "  Headline   \n\n\n\t  Details about   earnings.   ";
    String sanitized = sanitizer.sanitize(messy);
    assertThat(sanitized).isEqualTo("Headline Details about earnings.");
  }
}
