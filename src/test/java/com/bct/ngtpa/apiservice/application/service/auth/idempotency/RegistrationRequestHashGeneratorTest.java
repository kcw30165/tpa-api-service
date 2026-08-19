package com.bct.ngtpa.apiservice.application.service.auth.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationRequestHashGeneratorTest {
  private final RegistrationRequestHashGenerator generator =
      new Sha256RegistrationRequestHashGenerator();

  @Test
  void sha256RequestHashIsDeterministic() {
    var first =
        generator.hash(
            RegistrationIdempotencyAction.REGISTRATION_START,
            List.of("HKID", "A1234567", "2000-01-01"));
    var second =
        generator.hash(
            RegistrationIdempotencyAction.REGISTRATION_START,
            List.of("HKID", "A1234567", "2000-01-01"));

    assertThat(first).isEqualTo(second);
    assertThat(first.value()).matches("^[a-f0-9]{64}$");
  }

  @Test
  void sha256RequestHashChangesWhenActionChanges() {
    var first = generator.hash(RegistrationIdempotencyAction.REGISTRATION_START, List.of("same"));
    var second = generator.hash(RegistrationIdempotencyAction.OTP_RESEND, List.of("same"));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void sha256RequestHashChangesWhenPartChanges() {
    var first =
        generator.hash(RegistrationIdempotencyAction.REGISTRATION_START, List.of("same", "one"));
    var second =
        generator.hash(RegistrationIdempotencyAction.REGISTRATION_START, List.of("same", "two"));

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void sha256RequestHashDoesNotExposeRawPartsInToStringOrException() {
    var hash =
        generator.hash(
            RegistrationIdempotencyAction.REGISTRATION_START, List.of("member@example.com"));

    assertThat(hash.toString()).doesNotContain("member@example.com");
    assertThat(hash.value()).doesNotContain("member@example.com");
  }
}
