package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import java.util.List;

public final class LoginOtpVerifyWebMapper {
  public MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>
      toMutationResponse(VerifyLoginOtpResult result) {
    if (result.status() == VerifyLoginOtpStatus.ACCOUNTS_READY) {
      return MutationResponse.success(
          ApiStatus.SUCCESS,
          payload(WorkflowNextStep.SELECT_ACCOUNT, result, List.of(), true),
          List.of());
    }

    WorkflowNextStep next =
        result.status() == VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED
            ? WorkflowNextStep.RESTART_FLOW
            : WorkflowNextStep.VERIFY_OTP;
    String code =
        switch (result.status()) {
          case INVALID -> LoginErrorKey.OTP_INVALID.value();
          case EXPIRED -> LoginErrorKey.OTP_EXPIRED.value();
          case VERIFY_ATTEMPTS_EXCEEDED -> LoginErrorKey.OTP_VERIFY_ATTEMPTS_EXCEEDED.value();
          case CSRF_INVALID -> LoginErrorKey.CSRF_INVALID.value();
          case AUTH_FLOW_INVALID -> LoginErrorKey.AUTH_FLOW_INVALID.value();
          case ACCOUNT_LIST_UNAVAILABLE -> LoginErrorKey.ACCOUNT_LIST_UNAVAILABLE.value();
          case ZERO_VALID_ACCOUNTS -> LoginErrorKey.NO_ACCESSIBLE_ACCOUNTS.value();
          case SESSION_CREATION_FAILED -> LoginErrorKey.SESSION_CREATION_FAILED.value();
          default -> SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value();
        };
    List<LoginOtpActionResponse> actions =
        result.status() == VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED
            ? List.of(new LoginOtpActionResponse(WorkflowNextStep.RESTART_FLOW.toString()))
            : List.of();
    ApiStatus status =
        result.status() == VerifyLoginOtpStatus.CSRF_INVALID
            ? ApiStatus.VALIDATION_FAILED
            : result.status() == VerifyLoginOtpStatus.SYSTEM_FAILED
                    || result.status() == VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE
                    || result.status() == VerifyLoginOtpStatus.SESSION_CREATION_FAILED
                ? ApiStatus.SYSTEM_ERROR
                : ApiStatus.BUSINESS_REJECTED;
    return MutationResponse.failure(
        status,
        payload(next, result, actions, false),
        List.of(
            ApiError.form(
                code,
                null,
                List.of(result.status() == VerifyLoginOtpStatus.INVALID ? "otpCode" : "PAGE"),
                result.status() == VerifyLoginOtpStatus.CSRF_INVALID ? "SERVER" : "BFF")));
  }

  public MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>> missing(
      String code, String target) {
    return MutationResponse.failure(
        ApiStatus.VALIDATION_FAILED,
        payload(WorkflowNextStep.VERIFY_OTP, null, List.of(), false),
        List.of(ApiError.form(code, null, List.of(target), "BFF")));
  }

  private LoginWorkflowResultResponse<LoginOtpVerifyDataResponse> payload(
      WorkflowNextStep step,
      VerifyLoginOtpResult result,
      List<LoginOtpActionResponse> actions,
      boolean exposeAuthenticatedCsrf) {
    List<LoginOtpAccountResponse> accounts =
        result == null
            ? List.of()
            : result.accounts().stream()
                .map(
                    account ->
                        new LoginOtpAccountResponse(
                            account.accountRef().value(),
                            account.policyNo(),
                            account.certNo(),
                            account.accountEnv(),
                            account.trustCode(),
                            account.schemeType()))
                .toList();
    String csrfToken =
        exposeAuthenticatedCsrf && result != null ? result.authenticatedCsrfToken() : null;
    return new LoginWorkflowResultResponse<>(
        step,
        new LoginOtpVerifyDataResponse(
            csrfToken,
            result == null ? null : result.remainingVerifyAttempts(),
            actions,
            accounts));
  }

  public MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>> csrfInvalid() {
    return MutationResponse.failure(
        ApiStatus.VALIDATION_FAILED,
        payload(WorkflowNextStep.VERIFY_OTP, null, List.of(), false),
        List.of(
            ApiError.form(LoginErrorKey.CSRF_INVALID.value(), null, List.of("PAGE"), "SERVER")));
  }
}
