package com.bct.ngtpa.apiservice.adapter.out.security.registration.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayState;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmRegistrationStartReplayProtectorTest {
  private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void roundTripUsesCiphertextAndRedactedDiagnostics() {
    var protector = new AesGcmRegistrationStartReplayProtector("v1", SECRET);
    var reference = new ProtectedReplayReference("registrationStartReplayReference001");
    var state = new RegistrationStartProtectedReplayState("csrf-secret", "session-secret");

    var protectedReplay = protector.protect(reference, state);

    assertThat(protectedReplay.ciphertext()).doesNotContain("csrf-secret", "session-secret");
    assertThat(protectedReplay.toString())
        .doesNotContain(protectedReplay.iv(), protectedReplay.ciphertext());
    assertThat(protector.unprotect(reference, protectedReplay)).isEqualTo(state);
  }

  @Test
  void referenceSubstitutionFailsAuthentication() {
    var protector = new AesGcmRegistrationStartReplayProtector("v1", SECRET);
    var original = new ProtectedReplayReference("registrationStartReplayReference001");
    var substituted = new ProtectedReplayReference("registrationStartReplayReference002");
    var protectedReplay =
        protector.protect(
            original, new RegistrationStartProtectedReplayState("csrf-secret", "session-secret"));

    assertThatThrownBy(() -> protector.unprotect(substituted, protectedReplay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Registration Start protected replay is invalid");
  }

  @Test
  void wrongKeyVersionAndInvalidSecretsFailClosedWithoutEchoingSecret() {
    var protector = new AesGcmRegistrationStartReplayProtector("v1", SECRET);
    var reference = new ProtectedReplayReference("registrationStartReplayReference001");
    var protectedReplay =
        protector.protect(
            reference, new RegistrationStartProtectedReplayState("csrf-secret", "session-secret"));
    var wrongVersion =
        new com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency
            .ProtectedRegistrationStartReplay(
            1,
            "v2",
            protectedReplay.algorithm(),
            protectedReplay.iv(),
            protectedReplay.ciphertext());

    assertThatThrownBy(() -> protector.unprotect(reference, wrongVersion))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AesGcmRegistrationStartReplayProtector("v1", "not-base64!"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Registration Start replay secret is invalid")
        .hasMessageNotContaining("not-base64!");
    String shortSecret = Base64.getEncoder().encodeToString(new byte[8]);
    assertThatThrownBy(() -> new AesGcmRegistrationStartReplayProtector("v1", shortSecret))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Registration Start replay secret has invalid key length")
        .hasMessageNotContaining(shortSecret);
  }
}
