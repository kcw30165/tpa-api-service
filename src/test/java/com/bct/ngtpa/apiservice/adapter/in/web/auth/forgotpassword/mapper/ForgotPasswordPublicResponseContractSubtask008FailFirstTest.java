package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordPublicResponseContractSubtask008FailFirstTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void forgotPasswordErrorsExposeOnlyMachineReadableCodesTargetsAndSteps() {
    var startMapper = new ForgotPasswordStartWebMapper(Duration.ofMinutes(5));
    var identityMapper = new ForgotPasswordIdentityWebMapper();
    var detailsMapper = new ForgotPasswordDetailsOtpWebMapper();
    var resendMapper = new ForgotPasswordOtpResendWebMapper();
    var verifyMapper = new ForgotPasswordOtpVerifyWebMapper();
    var completeMapper = new ForgotPasswordCompleteWebMapper();

    assertMachineReadable(
        startMapper.toMutationResponse(
            new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
                WorkflowNextStep.ENTER_USERNAME,
                null,
                null,
                null,
                List.of(
                    new StartForgotPasswordError(
                        "err.forgotpassword.username.notFound",
                        "username",
                        "localized display text must not leak")),
                null),
            "en"),
        ApiStatus.BUSINESS_REJECTED,
        WorkflowNextStep.ENTER_USERNAME,
        "err.forgotpassword.username.notFound",
        List.of("username"));

    assertMachineReadable(
        startMapper.systemUnavailable(),
        ApiStatus.SYSTEM_ERROR,
        WorkflowNextStep.NONE,
        "err.workflow.system.unavailable",
        List.of());

    assertMachineReadable(
        identityMapper.response(
            VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.INVALID_TYPE)),
        ApiStatus.VALIDATION_FAILED,
        null,
        "err.forgot-password.incorrect.idType",
        List.of("identityType"));

    assertMachineReadable(
        identityMapper.response(
            VerifyForgotPasswordIdentityResult.of(
                VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED)),
        ApiStatus.SYSTEM_ERROR,
        null,
        "err.forgot-password.system.unavailable",
        List.of());

    assertMachineReadable(
        detailsMapper.map(
            new ForgotPasswordDetailsOtpResult(
                ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED,
                null,
                0,
                0,
                List.of("email"))),
        ApiStatus.VALIDATION_FAILED,
        null,
        "err.forgot-password.details.invalid",
        List.of("email"));

    assertMachineReadable(
        detailsMapper.map(
            new ForgotPasswordDetailsOtpResult(
                ForgotPasswordDetailsOtpResult.Status.EXPIRED, null, 0, 0, List.of())),
        ApiStatus.BUSINESS_REJECTED,
        WorkflowNextStep.RESEND_OTP,
        "err.forgot-password.otp.expired",
        List.of());

    assertMachineReadable(
        resendMapper.toMutationResponse(
            new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE,
                "m****r@example.com",
                null,
                null,
                4,
                25)),
        ApiStatus.BUSINESS_REJECTED,
        WorkflowNextStep.VERIFY_OTP,
        "err.forgot-password.otp.resend.cooldown",
        List.of());

    assertMachineReadable(
        verifyMapper.toMutationResponse(
            new VerifyForgotPasswordOtpResult(
                VerifyForgotPasswordOtpStatus.OTP_INVALID, WorkflowNextStep.VERIFY_OTP, null)),
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.VERIFY_OTP,
        "err.forgot-password.otp.invalid",
        List.of("code"));

    assertMachineReadable(
        verifyMapper.toMutationResponse(
            new VerifyForgotPasswordOtpResult(
                VerifyForgotPasswordOtpStatus.OTP_EXPIRED, WorkflowNextStep.RESEND_OTP, null)),
        ApiStatus.BUSINESS_REJECTED,
        WorkflowNextStep.RESEND_OTP,
        "err.forgot-password.otp.expired",
        List.of());

    assertMachineReadable(
        completeMapper.toResponse(
            new CompleteForgotPasswordResult(
                CompleteForgotPasswordStatus.VALIDATION_FAILED,
                WorkflowNextStep.PASSWORD_SETUP,
                false,
                false,
                List.of(
                    new WorkflowErrorDescriptor(
                        WorkflowErrorType.FIELD,
                        "err.forgot-password.password.invalid",
                        "localized password text must not leak",
                        List.of("password"),
                        WorkflowErrorSeverity.ERROR,
                        "BFF")))),
        ApiStatus.VALIDATION_FAILED,
        WorkflowNextStep.PASSWORD_SETUP,
        "err.forgot-password.password.invalid",
        List.of("password"));

    assertMachineReadable(
        completeMapper.systemUnavailable(),
        ApiStatus.SYSTEM_ERROR,
        WorkflowNextStep.NONE,
        "err.system.unavailable",
        List.of());
  }

  @Test
  void forgotPasswordStartResponseIsLanguageInvariant() {
    var mapper = new ForgotPasswordStartWebMapper(Duration.ofMinutes(5));
    var result =
        new StartForgotPasswordResult(
            StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
            WorkflowNextStep.ENTER_USERNAME,
            null,
            null,
            null,
            List.of(
                new StartForgotPasswordError(
                    "err.forgotpassword.username.notFound",
                    "username",
                    "should not vary by language")),
            null);

    var english = mapper.toMutationResponse(result, "en");
    var chinese = mapper.toMutationResponse(result, "zh-HK");

    assertThat(english.status()).isEqualTo(chinese.status());
    assertThat(english.result()).isEqualTo(chinese.result());
    assertThat(english.messages()).isEqualTo(chinese.messages());
    assertThat(english.errors()).isEqualTo(chinese.errors());
  }

  private void assertMachineReadable(
      MutationResponse<? extends ForgotPasswordWorkflowResultResponse<?>> response,
      ApiStatus expectedStatus,
      WorkflowNextStep expectedNextStep,
      String expectedCode,
      List<String> expectedTargets) {
    assertThat(response.status()).isEqualTo(expectedStatus);
    assertThat(response.messages()).isEmpty();
    if (expectedNextStep == null) {
      assertThat(response.result()).isNull();
    } else {
      assertThat(response.result()).isNotNull();
      assertThat(response.result().nextStep()).isEqualTo(expectedNextStep);
    }
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo(expectedCode);
              assertThat(error.message()).isNull();
              assertThat(error.targets()).containsExactlyElementsOf(expectedTargets);
              assertThat(error.targets()).doesNotContain("PAGE");
            });

    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.path("messages").isArray()).isTrue();
    assertThat(json.path("messages")).isEmpty();
    assertThat(json.toString())
        .doesNotContain(
            "\"message\"",
            "PAGE",
            "\"pageId\"",
            "\"pageRef\"",
            "\"labels\"",
            "\"description\"",
            "\"button\"");
    assertThat(json.at("/errors/0/code").asText()).isEqualTo(expectedCode);
  }
}
