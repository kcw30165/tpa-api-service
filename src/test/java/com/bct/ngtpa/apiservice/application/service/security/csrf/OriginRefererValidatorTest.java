package com.bct.ngtpa.apiservice.application.service.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OriginRefererValidatorTest {

  private final OriginRefererValidator validator = new OriginRefererValidator();

  @Test
  void allowsExactOrigin() {
    var result =
        validator.validate(
            "https://portal.example.com", null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.VALID);
    assertThat(result.valid()).isTrue();
  }

  @Test
  void rejectsDifferentScheme() {
    var result =
        validator.validate(
            "http://portal.example.com", null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsDifferentHost() {
    var result =
        validator.validate(
            "https://evil-portal.example.com", null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsHostSuffixAttack() {
    var result =
        validator.validate(
            "https://portal.example.com.evil.com", null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsDifferentPort() {
    var result =
        validator.validate("http://localhost:4300", null, List.of("http://localhost:4200"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void fallsBackToRefererWhenOriginMissing() {
    var result =
        validator.validate(
            null,
            "https://portal.example.com/path/to/page?x=1",
            List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.VALID);
    assertThat(result.valid()).isTrue();
  }

  @Test
  void rejectsRefererFromDisallowedOrigin() {
    var result =
        validator.validate(
            null, "https://evil.example.com/path", List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.REFERER_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsMissingOriginAndReferer() {
    var result = validator.validate(null, null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.MISSING);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsMalformedOriginSafely() {
    var result = validator.validate(":// bad", null, List.of("https://portal.example.com"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.MALFORMED);
    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsWildcardAllowedOriginForCredentialedFlow() {
    var result = validator.validate("https://portal.example.com", null, List.of("*"));

    assertThat(result.status()).isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    assertThat(result.valid()).isFalse();
  }
}
