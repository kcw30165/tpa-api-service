package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ForgotPasswordStartProtectedReplayFoundationTest {
  @Test
  void stateRedactsSessionAndIdentity() {
    var state =
        new ForgotPasswordStartProtectedReplayState(
            "afs_secret", "STAFF_NO", Instant.parse("2026-09-06T14:00:00Z"));

    assertThat(state.authFlowSessionId()).isEqualTo("afs_secret");
    assertThat(state.identityType()).isEqualTo("STAFF_NO");
    assertThat(state.toString()).doesNotContain("afs_secret", "STAFF_NO");
  }

  @Test
  void stateRejectsMissingProtectedContinuity() {
    assertThatThrownBy(
            () ->
                new ForgotPasswordStartProtectedReplayState(
                    " ", "STAFF_NO", Instant.parse("2026-09-06T14:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ForgotPasswordStartProtectedReplayState(
                    "afs_secret", " ", Instant.parse("2026-09-06T14:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void secureGeneratorProducesOpaqueDistinctReferences() {
    var generator = new SecureForgotPasswordStartProtectedReplayReferenceGenerator();
    var first = generator.generate();
    var second = generator.generate();

    assertThat(first).isNotEqualTo(second);
    assertThat(first.value()).hasSize(43);
    assertThat(first.toString()).doesNotContain(first.value());
  }
}
