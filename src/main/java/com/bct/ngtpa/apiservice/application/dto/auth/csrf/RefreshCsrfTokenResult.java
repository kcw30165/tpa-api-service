package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

import java.util.Objects;

public record RefreshCsrfTokenResult(
    RefreshCsrfTokenStatus status, CsrfRefreshSessionType sessionType, String csrfToken) {

  public RefreshCsrfTokenResult {
    Objects.requireNonNull(status, "status must not be null");
    if (status == RefreshCsrfTokenStatus.SUCCESS) {
      Objects.requireNonNull(sessionType, "sessionType must not be null on success");
      if (csrfToken == null || csrfToken.isBlank()) {
        throw new IllegalArgumentException("csrfToken must not be blank on success");
      }
    } else if (sessionType != null || csrfToken != null) {
      throw new IllegalArgumentException("failure result must not contain session or token data");
    }
  }

  public static RefreshCsrfTokenResult success(
      CsrfRefreshSessionType sessionType, String csrfToken) {
    return new RefreshCsrfTokenResult(RefreshCsrfTokenStatus.SUCCESS, sessionType, csrfToken);
  }

  public static RefreshCsrfTokenResult failure(RefreshCsrfTokenStatus status) {
    if (status == RefreshCsrfTokenStatus.SUCCESS) {
      throw new IllegalArgumentException("SUCCESS requires session and token data");
    }
    return new RefreshCsrfTokenResult(status, null, null);
  }

  public boolean successful() {
    return status == RefreshCsrfTokenStatus.SUCCESS;
  }

  @Override
  public String toString() {
    return "RefreshCsrfTokenResult[status="
        + status
        + ", sessionType="
        + sessionType
        + ", csrfToken="
        + (csrfToken == null ? null : "<redacted>")
        + "]";
  }
}
