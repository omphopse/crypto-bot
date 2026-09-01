package io.algopilot.research;

import static org.assertj.core.api.Assertions.assertThat;

import io.algopilot.research.security.DomainSecurityValidator;
import java.net.InetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DomainSecurityValidatorTest {
  private DomainSecurityValidator validator;

  @BeforeEach
  void setUp() {
    validator = new DomainSecurityValidator();
  }

  @Test
  void testValidHttpsUrl_accepted() {
    DomainSecurityValidator.ValidationResult res = validator.validateUrl("https://www.sec.gov/edgar/searchedgar/companysearch");
    assertThat(res.isValid()).isTrue();
    assertThat(res.normalizedUrl()).isEqualTo("https://www.sec.gov/edgar/searchedgar/companysearch");
  }

  @Test
  void testNonHttpsScheme_rejected() {
    assertThat(validator.validateUrl("http://example.com").isValid()).isFalse();
    assertThat(validator.validateUrl("file:///etc/passwd").isValid()).isFalse();
    assertThat(validator.validateUrl("javascript:alert(1)").isValid()).isFalse();
    assertThat(validator.validateUrl("data:text/html,test").isValid()).isFalse();
  }

  @Test
  void testLocalhostAndLoopback_rejected() {
    assertThat(validator.validateUrl("https://localhost/api").isValid()).isFalse();
    assertThat(validator.validateUrl("https://127.0.0.1/admin").isValid()).isFalse();
    assertThat(validator.validateUrl("https://foo.localhost/").isValid()).isFalse();
  }

  @Test
  void testMetadataService_rejected() {
    assertThat(validator.validateUrl("https://metadata.google.internal/computeMetadata/v1/").isValid()).isFalse();
    assertThat(validator.validateUrl("https://instance-data/latest/meta-data/").isValid()).isFalse();
  }

  @Test
  void testPrivateIpRanges_identifiedAsPrivate() throws Exception {
    assertThat(validator.isPrivateOrLocalIp(InetAddress.getByName("10.0.1.5"))).isTrue();
    assertThat(validator.isPrivateOrLocalIp(InetAddress.getByName("172.20.0.1"))).isTrue();
    assertThat(validator.isPrivateOrLocalIp(InetAddress.getByName("192.168.1.1"))).isTrue();
    assertThat(validator.isPrivateOrLocalIp(InetAddress.getByName("169.254.169.254"))).isTrue();
    assertThat(validator.isPrivateOrLocalIp(InetAddress.getByName("127.0.0.1"))).isTrue();
  }

  @Test
  void testNonStandardPort_rejected() {
    DomainSecurityValidator.ValidationResult res = validator.validateUrl("https://example.com:8080/internal");
    assertThat(res.isValid()).isFalse();
    assertThat(res.reason()).contains("Non-standard HTTPS port");
  }
}
