package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayState;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmForgotPasswordStartReplayProtectorTest {
  private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void roundTripUsesCiphertextAndReferenceBoundAuthentication() {
    var protector = new AesGcmForgotPasswordStartReplayProtector("v1", SECRET);
    var reference = new ProtectedReplayReference("forgotPasswordStartReplayReference001");
    var state =
        new ForgotPasswordStartProtectedReplayState(
            "afs-secret", "STAFF_NO", Instant.parse("2026-09-06T14:00:00Z"));
    var protectedReplay = protector.protect(reference, state);
    assertThat(protectedReplay.ciphertext()).doesNotContain("afs-secret", "STAFF_NO");
    assertThat(protectedReplay.toString())
        .doesNotContain(protectedReplay.iv(), protectedReplay.ciphertext());
    assertThat(protector.unprotect(reference, protectedReplay)).isEqualTo(state);
    assertThatThrownBy(
            () ->
                protector.unprotect(
                    new ProtectedReplayReference("forgotPasswordStartReplayReference002"),
                    protectedReplay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Forgot Password Start protected replay is invalid");
  }

  @Test
  void invalidSecretsFailClosedWithoutEchoingSecret() {
    assertThatThrownBy(() -> new AesGcmForgotPasswordStartReplayProtector("v1", "not-base64!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Forgot Password Start replay secret is invalid")
        .hasMessageNotContaining("not-base64!");
  }
}
