package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenResult;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CsrfTokenRefreshHttpContractTest {
  private final CsrfTokenRefreshHttpResponses responses = new CsrfTokenRefreshHttpResponses();

  @Test
  void successReturnsOnlySessionTypeAndRawTokenInResult() {
    var entity =
        responses.toResponse(
            RefreshCsrfTokenResult.success(CsrfRefreshSessionType.AUTHENTICATED, "raw-token"));
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getBody().success()).isTrue();
    assertThat(entity.getBody().result().csrfToken()).isEqualTo("raw-token");
    assertThat(entity.getBody().result().toString()).doesNotContain("raw-token");
  }

  @Test
  void typedFailuresMapToApprovedHttpFamiliesWithoutResults() {
    assertThat(
            responses
                .toResponse(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.SESSION_MISSING))
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            responses
                .toResponse(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.ORIGIN_REJECTED))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            responses
                .toResponse(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.CONFLICT))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            responses
                .toResponse(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
                .getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
