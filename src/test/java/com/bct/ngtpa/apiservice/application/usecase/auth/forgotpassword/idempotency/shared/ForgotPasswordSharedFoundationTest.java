package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordSharedFoundationTest {
  private final ForgotPasswordSharedIdempotencyIdentityFactory identities =
      new ForgotPasswordSharedIdempotencyIdentityFactory();
  private final ForgotPasswordSharedFingerprintFactory fingerprints =
      new ForgotPasswordSharedFingerprintFactory();

  @Test
  void exposesOnlyFiveMigratedActions() {
    assertThat(ForgotPasswordSharedIdempotencyAction.values())
        .containsExactly(
            ForgotPasswordSharedIdempotencyAction.START,
            ForgotPasswordSharedIdempotencyAction.DETAILS_AND_OTP,
            ForgotPasswordSharedIdempotencyAction.OTP_RESEND,
            ForgotPasswordSharedIdempotencyAction.OTP_VERIFY,
            ForgotPasswordSharedIdempotencyAction.COMPLETE);
  }

  @Test
  void hashesAuthorizedScopeAndPreservesActionIsolation() {
    var first = identities.create(ForgotPasswordSharedIdempotencyAction.START, "scope-a", "abcdefgh");
    var second =
        identities.create(ForgotPasswordSharedIdempotencyAction.OTP_VERIFY, "scope-a", "abcdefgh");
    assertThat(first.capability().value()).isEqualTo("forgot-password");
    assertThat(first.scope().value()).hasSize(64).doesNotContain("scope-a");
    assertThat(first.action()).isNotEqualTo(second.action());
    assertThat(first.toString()).doesNotContain("scope-a", "abcdefgh");
  }

  @Test
  void createsVersionedActionSeparatedFingerprintsWithoutOtpOrPasswordInputs() {
    assertThat(fingerprints.start("member").version()).isEqualTo(1);
    assertThat(fingerprints.detailsAndOtp(List.of("1990-01-01", "a@example.test")))
        .isNotEqualTo(fingerprints.start("member"));
    assertThat(fingerprints.otpResend()).isNotEqualTo(fingerprints.otpVerify());
    assertThat(fingerprints.otpVerify()).isNotEqualTo(fingerprints.complete());
  }

  @Test
  void rejectsBlankIdentityInputs() {
    assertThatThrownBy(
            () ->
                identities.create(
                    ForgotPasswordSharedIdempotencyAction.START, " ", "abcdefgh"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void classifiersRemainExplicitAndConservative() {
    RuntimeException failure = new RuntimeException("safe");
    assertThat(ForgotPasswordSharedFailureClassifiers.provenPreDispatch().classify(failure))
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(ForgotPasswordSharedFailureClassifiers.possibleSideEffect().classify(failure))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }
}
