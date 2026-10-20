package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.LoginErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class LoginHttpResponsesTest {

  @Test
  void nullBodyMapsToInternalServerError() {
    var response = LoginHttpResponses.toResponse(null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void successBodyMapsToOk() {
    MutationResponse<String> body = MutationResponse.success(ApiStatus.SUCCESS, "ok", List.of());
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void csrfInvalidCodeMapsToForbidden() {
    assertThat(statusFor(LoginErrorKey.CSRF_INVALID.value())).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void originInvalidCodeMapsToForbidden() {
    assertThat(statusFor(LoginErrorKey.ORIGIN_INVALID.value())).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void idempotencyInProgressCodeMapsToConflict() {
    assertThat(statusFor(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value()))
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void idempotencyKeyReusedCodeMapsToConflict() {
    assertThat(statusFor(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value()))
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void otpVerifyAttemptsExceededCodeMapsToTooManyRequests() {
    assertThat(statusFor(LoginErrorKey.OTP_VERIFY_ATTEMPTS_EXCEEDED.value()))
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void otpCooldownActiveCodeMapsToTooManyRequests() {
    assertThat(statusFor(LoginErrorKey.OTP_COOLDOWN_ACTIVE.value()))
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void accountLockedCodeMapsToLocked() {
    assertThat(statusFor(LoginErrorKey.ACCOUNT_LOCKED.value())).isEqualTo(HttpStatus.LOCKED);
  }

  @Test
  void invalidAccountCodeMapsToUnauthorized() {
    assertThat(statusFor(LoginErrorKey.INVALID_ACCOUNT.value()))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void credentialsInvalidCodeMapsToUnauthorized() {
    assertThat(statusFor(LoginErrorKey.CREDENTIALS_INVALID.value()))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void credentialsUnavailableCodeMapsToBadGateway() {
    assertThat(statusFor(LoginErrorKey.CREDENTIALS_UNAVAILABLE.value()))
        .isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void sessionCreationFailedCodeMapsToServiceUnavailable() {
    assertThat(statusFor(LoginErrorKey.SESSION_CREATION_FAILED.value()))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void systemFailedCodeMapsToInternalServerError() {
    assertThat(statusFor(LoginErrorKey.SYSTEM_FAILED.value()))
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void unmappedCodeWithBusinessRejectedStatusMapsToUnprocessableEntity() {
    MutationResponse<Object> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(ApiError.form("err.unmapped.code", null, List.of("PAGE"), "BFF")));
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode().value())
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
  }

  @Test
  void unmappedCodeWithSystemErrorStatusMapsToBadGateway() {
    MutationResponse<Object> body =
        MutationResponse.failure(
            ApiStatus.SYSTEM_ERROR,
            List.of(ApiError.form("err.unmapped.code", null, List.of("PAGE"), "BFF")));
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void unmappedCodeWithOtherStatusMapsToBadRequest() {
    MutationResponse<Object> body =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(ApiError.form("err.unmapped.code", null, List.of("PAGE"), "BFF")));
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void emptyErrorsListFallsBackToEmptyCodeAndBadRequest() {
    MutationResponse<Object> body =
        MutationResponse.failure(ApiStatus.VALIDATION_FAILED, List.of());
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void nullFirstErrorCodeFallsBackToEmptyCodeAndBadRequest() {
    MutationResponse<Object> body =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(ApiError.form(null, null, List.of("PAGE"), "BFF")));
    var response = LoginHttpResponses.toResponse(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private static HttpStatusCode statusFor(String code) {
    MutationResponse<Object> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(ApiError.form(code, null, List.of("PAGE"), "BFF")));
    return LoginHttpResponses.toResponse(body).getStatusCode();
  }
}
