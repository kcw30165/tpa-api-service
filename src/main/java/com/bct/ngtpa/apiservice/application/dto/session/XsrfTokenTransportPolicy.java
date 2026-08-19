package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;

public record XsrfTokenTransportPolicy(SessionCookiePolicy cookiePolicy, String headerName) {

  public XsrfTokenTransportPolicy {
    Objects.requireNonNull(cookiePolicy, "cookiePolicy must not be null");
    Objects.requireNonNull(headerName, "headerName must not be null");
    if (headerName.isBlank()) {
      throw new IllegalArgumentException("XSRF header name is required");
    }
    if (cookiePolicy.httpOnly()) {
      throw new IllegalArgumentException("XSRF token cookie must not be HttpOnly");
    }
  }
}
