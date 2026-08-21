package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import org.junit.jupiter.api.Test;

class PreAuthSessionCsrfRefreshServiceContractTest {
  @Test
  void classifiesMissingAndUnusableSessionSeparately() {
    assertThat(
            PreAuthSessionCsrfRefreshService.classifyRotationFailure(
                new IllegalStateException("Auth flow session not available")))
        .isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING);
    assertThat(
            PreAuthSessionCsrfRefreshService.classifyRotationFailure(
                new IllegalStateException("Auth flow session is not usable")))
        .isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE);
  }

  @Test
  void classifiesRevisionConflictAndUnknownFailureSeparately() {
    assertThat(
            PreAuthSessionCsrfRefreshService.classifyRotationFailure(
                new RevisionConflictException("concurrent mutation")))
        .isEqualTo(RefreshCsrfTokenStatus.CONFLICT);
    assertThat(
            PreAuthSessionCsrfRefreshService.classifyRotationFailure(
                new IllegalStateException("redis unavailable")))
        .isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE);
  }

  private static final class RevisionConflictException extends RuntimeException {
    private RevisionConflictException(String message) {
      super(message);
    }
  }
}
