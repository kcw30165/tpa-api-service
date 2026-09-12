package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginStartWebContractStabilizationTest {
  private final LoginStartWebMapper mapper = new LoginStartWebMapper();

  @Test
  void everyApplicationStatusHasStableApiAndHttpMapping() {
    assertMapping(
        StartLoginStatus.INCORRECT_PASSWORD,
        ApiStatus.BUSINESS_REJECTED,
        "err.login.credentials.invalid",
        HttpStatus.UNAUTHORIZED);
    assertMapping(
        StartLoginStatus.INCORRECT_USERNAME,
        ApiStatus.BUSINESS_REJECTED,
        "err.login.credentials.invalid",
        HttpStatus.UNAUTHORIZED);
    assertMapping(
        StartLoginStatus.INVALID_ACCOUNT,
        ApiStatus.BUSINESS_REJECTED,
        "err.login.invalid.account",
        HttpStatus.UNAUTHORIZED);
    assertMapping(
        StartLoginStatus.ACCOUNT_LOCKED,
        ApiStatus.BUSINESS_REJECTED,
        "err.login.locked.account",
        HttpStatus.LOCKED);
    assertMapping(
        StartLoginStatus.DUPLICATE_IN_PROGRESS,
        ApiStatus.BUSINESS_REJECTED,
        "err.workflow.idempotency.inProgress",
        HttpStatus.CONFLICT);
    assertMapping(
        StartLoginStatus.KEY_REUSED,
        ApiStatus.VALIDATION_FAILED,
        "err.workflow.idempotency.keyReused",
        HttpStatus.CONFLICT);
    assertMapping(
        StartLoginStatus.FAILED_EXISTING,
        ApiStatus.BUSINESS_REJECTED,
        "err.login.credentials.invalid",
        HttpStatus.UNAUTHORIZED);
    assertMapping(
        StartLoginStatus.UNKNOWN_OUTCOME,
        ApiStatus.SYSTEM_ERROR,
        "err.login.session.creationFailed",
        HttpStatus.SERVICE_UNAVAILABLE);
    assertMapping(
        StartLoginStatus.UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "err.login.credentials.unavailable",
        HttpStatus.BAD_GATEWAY);
  }

  @Test
  void missingRequestIdAndReactiveFailureHaveCanonicalBlockingErrors() {
    var missing = mapper.missingRequestId();
    assertThat(LoginHttpResponses.toResponse(missing).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.request.id.missing");
              assertThat(error.targets()).containsExactly("X-Request-Id");
            });

    var unavailable = mapper.systemUnavailable();
    assertThat(LoginHttpResponses.toResponse(unavailable).getStatusCode())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(unavailable.errors())
        .singleElement()
        .extracting(error -> error.code())
        .isEqualTo("err.workflow.system.unavailable");
  }

  private void assertMapping(
      StartLoginStatus status,
      ApiStatus expectedApiStatus,
      String expectedCode,
      HttpStatus expectedHttpStatus) {
    var body =
        mapper.toMutationResponse(
            new StartLoginResult(status, null, null, null, List.of(), 2, null), "en");
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(expectedApiStatus);
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors())
        .singleElement()
        .extracting(error -> error.code())
        .isEqualTo(expectedCode);
    assertThat(LoginHttpResponses.toResponse(body).getStatusCode()).isEqualTo(expectedHttpStatus);
  }
}
