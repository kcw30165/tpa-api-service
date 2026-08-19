package com.bct.ngtpa.apiservice.application.dto.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class XsrfTokenTransportPolicyTest {

  @Test
  void xsrfTokenCookieIsNotHttpOnly() {
    var policy = transportPolicy();
    assertThat(policy.cookiePolicy().name()).isEqualTo("XSRF-TOKEN");
    assertThat(policy.cookiePolicy().path()).isEqualTo("/api/v1/auth");
    assertThat(policy.cookiePolicy().maxAgeSeconds()).isEqualTo(1800);
    assertThat(policy.cookiePolicy().httpOnly()).isFalse();
    assertThat(policy.cookiePolicy().secure()).isTrue();
    assertThat(policy.cookiePolicy().sameSite()).isEqualTo(SessionCookieSameSite.NONE);
  }

  @Test
  void xsrfTransportPolicyExposesHeaderName() {
    assertThat(transportPolicy().headerName()).isEqualTo("X-XSRF-TOKEN");
  }

  @Test
  void xsrfTransportPolicyRejectsBlankHeaderName() {
    assertThatThrownBy(() -> new XsrfTokenTransportPolicy(xsrfCookiePolicy(), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("XSRF header name is required");
  }

  @Test
  void xsrfTransportPolicyDoesNotContainRawTokenValue() {
    assertThat(transportPolicy().toString()).doesNotContain("csrf_secret_value");
  }

  private static XsrfTokenTransportPolicy transportPolicy() {
    return new XsrfTokenTransportPolicy(xsrfCookiePolicy(), "X-XSRF-TOKEN");
  }

  private static SessionCookiePolicy xsrfCookiePolicy() {
    return new SessionCookiePolicy(
        "XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE);
  }
}
