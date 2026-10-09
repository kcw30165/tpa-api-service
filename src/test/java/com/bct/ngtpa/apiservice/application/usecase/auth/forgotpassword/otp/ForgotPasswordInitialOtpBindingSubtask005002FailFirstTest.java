package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import org.junit.jupiter.api.Test;

class ForgotPasswordInitialOtpBindingSubtask005002FailFirstTest {
  @Test
  void addsApprovedScenarioAndProtectedPhaseVocabulary() {
    assertThat(OtpScenario.valueOf("FORGOT_PASSWORD")).isSameAs(OtpScenario.FORGOT_PASSWORD);
    assertThat(MessageDeliveryScenario.valueOf("FORGOT_PASSWORD"))
        .isSameAs(MessageDeliveryScenario.FORGOT_PASSWORD);
    assertThat(MessageDeliveryType.valueOf("FORGOT_PASSWORD_OTP"))
        .isSameAs(MessageDeliveryType.FORGOT_PASSWORD_OTP);
    assertThat(ForgotPasswordProtectedPhase.valueOf("OTP_SENT"))
        .isSameAs(ForgotPasswordProtectedPhase.OTP_SENT);
    assertThat(ForgotPasswordProtectedPhase.valueOf("OTP_DELIVERY_UNKNOWN"))
        .isSameAs(ForgotPasswordProtectedPhase.OTP_DELIVERY_UNKNOWN);
  }

  @Test
  void policyIsApprovedAndFailsFast() {
    var policy = ForgotPasswordInitialOtpPolicy.approved();
    assertThat(policy.length()).isEqualTo(6);
    assertThat(policy.numericOnly()).isTrue();
    assertThat(policy.ttlSeconds()).isEqualTo(300);
    assertThat(policy.resendCooldownSeconds()).isEqualTo(60);
    assertThat(policy.maxVerifyAttempts()).isEqualTo(5);
    assertThat(policy.maxResendAttempts()).isEqualTo(5);
    assertThat(policy.invalidatePreviousOnResend()).isTrue();
    assertThatThrownBy(() -> new ForgotPasswordInitialOtpPolicy(0, true, 300, 60, 5, 5, true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // maskingMatchesLoginOutputAndRedactsSafeTypes removed: ForgotPasswordDestinationMasker
  // was deleted as part of the centralized masking policy migration (Phase 2). Equivalent
  // and more thorough coverage now lives in MaskingServiceTest.
}
