package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordIdempotencyIntegrationPolicyTest {
  private final ForgotPasswordIdempotencyIntegrationPolicy policy =
      new ForgotPasswordIdempotencyIntegrationPolicy(
          new IdempotencyOperationIdentityFactory(),
          new CanonicalIdempotencyFingerprintFactory());

  @Test
  void exposesOnlyFiveIntegrationActions() {
    assertThat(ForgotPasswordIdempotencyAction.values())
        .containsExactly(
            ForgotPasswordIdempotencyAction.START,
            ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
            ForgotPasswordIdempotencyAction.OTP_RESEND,
            ForgotPasswordIdempotencyAction.OTP_VERIFY,
            ForgotPasswordIdempotencyAction.COMPLETE);
  }

  @Test
  void suppliesPolicyInputsToCentralIdentityAndFingerprintPrimitives() {
    var first = policy.operationId(ForgotPasswordIdempotencyAction.START, "scope-a", "abcdefgh");
    var second = policy.operationId(ForgotPasswordIdempotencyAction.OTP_VERIFY, "scope-a", "abcdefgh");
    assertThat(first.capability().value()).isEqualTo("forgot-password");
    assertThat(first.scope().value()).hasSize(64).doesNotContain("scope-a");
    assertThat(first.action()).isNotEqualTo(second.action());
    assertThat(policy.start("member").version()).isEqualTo(1);
    assertThat(policy.detailsAndOtp(List.of("1990-01-01", "a@example.test")))
        .isNotEqualTo(policy.start("member"));
    assertThat(policy.otpResend()).isNotEqualTo(policy.otpVerify());
    assertThat(policy.otpVerify()).isNotEqualTo(policy.complete());
  }

  @Test
  void rejectsBlankPolicyInputs() {
    assertThatThrownBy(
            () -> policy.operationId(ForgotPasswordIdempotencyAction.START, " ", "abcdefgh"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
