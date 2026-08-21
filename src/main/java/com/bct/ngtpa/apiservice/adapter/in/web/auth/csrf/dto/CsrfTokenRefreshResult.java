package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto;

import java.util.Objects;

public record CsrfTokenRefreshResult(CsrfTokenRefreshData data) {
  public CsrfTokenRefreshResult {
    Objects.requireNonNull(data, "data must not be null");
  }
}
