package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.service.message.DefaultMessageTemplateRenderer;
import com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderCommand;
import java.util.Map;
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

  @Test
  void rendersApprovedEnglishAndTraditionalChineseTemplates() {
    var templates = ForgotPasswordOtpTemplates.approved();
    var renderer = new DefaultMessageTemplateRenderer();
    Map<String, String> variables = Map.of("code", "123456", "ttlValue", "5", "ttlUnit", "minutes");
    assertThat(
            renderer
                .render(new MessageTemplateRenderCommand(templates.sms("en"), variables))
                .renderedText())
        .isEqualTo(
            "Your BCT forgot password verification code is 123456. It is valid for 5 minutes.");
    assertThat(
            renderer
                .render(new MessageTemplateRenderCommand(templates.sms("zh_HK"), variables))
                .renderedText())
        .contains("123456", "5", "minutes");
    assertThat(templates.emailSubject("en")).isEqualTo("BCT forgot password verification code");
    assertThat(templates.emailSubject("zh-HK")).isEqualTo("BCT 忘記密碼驗證碼");
  }

  @Test
  void maskingMatchesLoginOutputAndRedactsSafeTypes() {
    var masker = new ForgotPasswordDestinationMasker();
    assertThat(masker.mask("email", "member@example.com")).isEqualTo("m*****@example.com");
    assertThat(masker.mask("mobile", "91234567")).isEqualTo("****4567");
    assertThat(masker.toString()).doesNotContain("member@example.com", "91234567");
  }
}
