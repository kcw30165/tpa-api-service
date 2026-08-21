package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import java.util.Objects;

public record CsrfTokenRefreshData(CsrfRefreshSessionType sessionType, String csrfToken) {
  public CsrfTokenRefreshData {
    Objects.requireNonNull(sessionType, "sessionType must not be null");
    if (csrfToken == null || csrfToken.isBlank()) {
      throw new IllegalArgumentException("csrfToken must not be blank");
    }
  }

  @Override
  public String toString() {
    return "CsrfTokenRefreshData[sessionType=" + sessionType + ", csrfToken=<redacted>]";
  }
}
