package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshCsrfTokenResultTest {
  @Test
  void successContainsSessionTypeAndRedactsRawToken() {
    var result = RefreshCsrfTokenResult.success(CsrfRefreshSessionType.AUTHENTICATED, "raw-secret");

    assertThat(result.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
    assertThat(result.successful()).isTrue();
    assertThat(result.csrfToken()).isEqualTo("raw-secret");
    assertThat(result.toString()).doesNotContain("raw-secret");
  }

  @Test
  void failureCannotContainSessionOrTokenData() {
    var result = RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.CONFLICT);

    assertThat(result.successful()).isFalse();
    assertThat(result.sessionType()).isNull();
    assertThat(result.csrfToken()).isNull();
    assertThatThrownBy(
            () ->
                new RefreshCsrfTokenResult(
                    RefreshCsrfTokenStatus.CONFLICT,
                    CsrfRefreshSessionType.PRE_AUTH,
                    "must-not-leak"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
