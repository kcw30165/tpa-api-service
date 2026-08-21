package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

import java.util.Objects;

public record RefreshCsrfTokenResult(CsrfRefreshSessionType sessionType, String csrfToken) {
  public RefreshCsrfTokenResult {
    Objects.requireNonNull(sessionType, "sessionType must not be null");
    if (csrfToken == null || csrfToken.isBlank()) {
      throw new IllegalArgumentException("csrfToken must not be blank");
    }
  }

  @Override
  public String toString() {
    return "RefreshCsrfTokenResult[sessionType=" + sessionType + ", csrfToken=<redacted>]";
  }
}
