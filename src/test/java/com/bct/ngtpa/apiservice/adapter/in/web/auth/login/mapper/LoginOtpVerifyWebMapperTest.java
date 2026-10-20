package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyWebMapperTest {
  final LoginOtpVerifyWebMapper m = new LoginOtpVerifyWebMapper();
  final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void invalidStaysOnVerifyOtp() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.INVALID, 3, false, java.util.List.of(), null, null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.otp.invalid");
    assertThat(r.result().data().csrfToken()).isNull();
  }

  @Test
  void exhaustionUsesRestartFlowAction() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED,
                0,
                true,
                java.util.List.of(),
                null,
                null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(r.result().data().actions().getFirst().code()).isEqualTo("RESTART_FLOW");
    assertThat(r.result().data().csrfToken()).isNull();
  }

  @Test
  void loginOtpActionsKeepCompactCodeOnlyJsonShape() throws Exception {
    var response =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED,
                0,
                true,
                java.util.List.of(),
                null,
                null));

    var json = objectMapper.writeValueAsString(response);

    assertThat(json)
        .contains("\"actions\":[{\"code\":\"RESTART_FLOW\"}]")
        .doesNotContain("pageId", "pageRef", "variant", "nextStep\":\"RETURN_TO_LOGIN\"");
  }
  @Test
  void expiredRemainsOnVerifyOtpWithOtpExpiredCode() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.EXPIRED, 2, false, java.util.List.of(), null, null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.otp.expired");
    assertThat(r.errors().getFirst().source()).isEqualTo("BFF");
  }

  @Test
  void csrfInvalidMapsToValidationFailedWithServerSource() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.CSRF_INVALID, null, false, java.util.List.of(), null, null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.csrf.invalid");
    assertThat(r.errors().getFirst().source()).isEqualTo("SERVER");
    assertThat(r.errors().getFirst().targets()).containsExactly("PAGE");
  }

  @Test
  void authFlowInvalidMapsToBusinessRejectedWithAuthFlowInvalidCode() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.AUTH_FLOW_INVALID,
                null,
                false,
                java.util.List.of(),
                null,
                null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.authFlow.invalid");
  }

  @Test
  void accountListUnavailableMapsToSystemErrorWithAccountListUnavailableCode() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE,
                null,
                false,
                java.util.List.of(),
                null,
                null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.accountList.unavailable");
  }

  @Test
  void zeroValidAccountsMapsToBusinessRejectedWithNoAccessibleAccountsCode() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS,
                null,
                false,
                java.util.List.of(),
                null,
                null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.accountList.empty");
  }

  @Test
  void sessionCreationFailedMapsToSystemErrorWithSessionCreationFailedCode() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.SESSION_CREATION_FAILED,
                null,
                false,
                java.util.List.of(),
                null,
                null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.session.creationFailed");
  }

  @Test
  void systemFailedMapsToSystemErrorWithSystemTypeApiError() {
    var r =
        m.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.SYSTEM_FAILED, null, false, java.util.List.of(), null, null));
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.otp.verify.system.failed");
    assertThat(r.errors().getFirst().type()).isEqualTo("SYSTEM");
    assertThat(r.errors().getFirst().targets()).isEmpty();
    assertThat(r.errors().getFirst().source()).isEqualTo("BFF");
  }

  @Test
  void missingReturnsValidationFailedWithGivenCodeAndTarget() {
    var r = m.missing("err.login.otp.required", "otpCode");
    assertThat(r.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.result().data().csrfToken()).isNull();
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.otp.required");
    assertThat(r.errors().getFirst().targets()).containsExactly("otpCode");
    assertThat(r.errors().getFirst().source()).isEqualTo("BFF");
  }

  @Test
  void csrfInvalidFactoryReturnsValidationFailedWithServerSourceCsrfCode() {
    var r = m.csrfInvalid();
    assertThat(r.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(r.result().data().csrfToken()).isNull();
    assertThat(r.errors().getFirst().code()).isEqualTo("err.login.csrf.invalid");
    assertThat(r.errors().getFirst().targets()).containsExactly("PAGE");
    assertThat(r.errors().getFirst().source()).isEqualTo("SERVER");
  }

}
