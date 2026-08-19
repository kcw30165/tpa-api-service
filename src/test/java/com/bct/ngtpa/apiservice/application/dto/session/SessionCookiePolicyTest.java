package com.bct.ngtpa.apiservice.application.dto.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionCookiePolicyTest {

  @Test
  void sessionCookiePolicyCanRepresentAuthFlowCookie() {
    var policy =
        new SessionCookiePolicy(
            "auth_flow_sid", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE);

    assertThat(policy.name()).isEqualTo("auth_flow_sid");
    assertThat(policy.path()).isEqualTo("/api/v1/auth");
    assertThat(policy.maxAgeSeconds()).isEqualTo(1800);
    assertThat(policy.httpOnly()).isTrue();
    assertThat(policy.secure()).isTrue();
    assertThat(policy.sameSite()).isEqualTo(SessionCookieSameSite.NONE);
  }

  @Test
  void sessionCookiePolicyCanRepresentXsrfCookie() {
    var policy =
        new SessionCookiePolicy(
            "XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE);

    assertThat(policy.name()).isEqualTo("XSRF-TOKEN");
    assertThat(policy.path()).isEqualTo("/api/v1/auth");
    assertThat(policy.maxAgeSeconds()).isEqualTo(1800);
    assertThat(policy.httpOnly()).isFalse();
    assertThat(policy.secure()).isTrue();
    assertThat(policy.sameSite()).isEqualTo(SessionCookieSameSite.NONE);
  }

  @Test
  void cookiePolicyRejectsUnsafeDefaults() {
    assertThatThrownBy(
            () ->
                new SessionCookiePolicy(
                    "", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Session cookie name is invalid");
    assertThatThrownBy(
            () ->
                new SessionCookiePolicy(
                    "auth_flow_sid", "", 1800, true, true, SessionCookieSameSite.NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Session cookie path is invalid");
    assertThatThrownBy(
            () ->
                new SessionCookiePolicy(
                    "auth_flow_sid", "/api/v1/auth", 0, true, true, SessionCookieSameSite.NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Session cookie maxAgeSeconds must be greater than zero");
    assertThatThrownBy(
            () ->
                new SessionCookiePolicy(
                    "auth_flow_sid", "/api/v1/auth", 1800, true, false, SessionCookieSameSite.NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SameSite=None cookies must be Secure");
  }
}
