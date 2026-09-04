package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import org.junit.jupiter.api.Test;

class LoginSharedIdempotencyBridgeTest {
  private final LoginSharedIdempotencyIdentityFactory identities =
      new LoginSharedIdempotencyIdentityFactory();
  private final LoginSharedFingerprintFactory fingerprints = new LoginSharedFingerprintFactory();

  @Test
  void exposesStableLoginActions() {
    assertThat(LoginSharedIdempotencyAction.CREDENTIALS.action().value()).isEqualTo("credentials");
    assertThat(LoginSharedIdempotencyAction.OTP_SEND.action().value()).isEqualTo("otp-send");
    assertThat(LoginSharedIdempotencyAction.OTP_RESEND.action().value()).isEqualTo("otp-resend");
    assertThat(LoginSharedIdempotencyAction.OTP_VERIFY.action().value()).isEqualTo("otp-verify");
  }

  @Test
  void hashesRawAuthorizedScopeBeforeBuildingIdentity() {
    String rawScope = "auth-flow-session-secret";
    var id =
        identities.create(LoginSharedIdempotencyAction.OTP_SEND, rawScope, "idem_key_12345678");

    assertThat(id.capability().value()).isEqualTo("login");
    assertThat(id.scope().value()).hasSize(64).doesNotContain(rawScope);
    assertThat(id.toString()).doesNotContain(rawScope).doesNotContain("idem_key_12345678");
  }

  @Test
  void createsDeterministicActionSeparatedVersionedFingerprints() {
    var first = fingerprints.otpSend("session-value", "destination-value");
    var same = fingerprints.otpSend("session-value", "destination-value");
    var resend = fingerprints.otpResend("session-value");

    assertThat(first.version()).isEqualTo(1);
    assertThat(first.value()).hasSize(64).isEqualTo(same.value()).isNotEqualTo(resend.value());
  }

  @Test
  void passwordIsAOneWayFingerprintInput() {
    String password = "Password-Secret";

    assertThat(fingerprints.credentials("member", password).value()).doesNotContain(password);
    assertThat(fingerprints.oneWayDigest(password)).hasSize(64).doesNotContain(password);
  }

  @Test
  void otpVerifyUsesTheEstablishedConstantRequestEquivalence() {
    var first = fingerprints.otpVerify();
    var same = fingerprints.otpVerify();

    assertThat(first.version()).isEqualTo(1);
    assertThat(first.value()).hasSize(64).isEqualTo(same.value());
  }

  @Test
  void preservesLegacyActionSemanticInputs() {
    assertThat(fingerprints.otpSend("session-a", "destination-a"))
        .isNotEqualTo(fingerprints.otpSend("session-a", "destination-b"));
    assertThat(fingerprints.otpResend("session-a"))
        .isNotEqualTo(fingerprints.otpResend("session-b"));
  }

  @Test
  void rejectsBlankIdentityAndFingerprintInputs() {
    assertThatThrownBy(
            () ->
                identities.create(
                    LoginSharedIdempotencyAction.CREDENTIALS, " ", "idem_key_12345678"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exposesOnlyExplicitFailureChoices() {
    assertThat(LoginSharedFailureClassifiers.provenPreDispatch().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(LoginSharedFailureClassifiers.possibleSideEffect().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }
}
