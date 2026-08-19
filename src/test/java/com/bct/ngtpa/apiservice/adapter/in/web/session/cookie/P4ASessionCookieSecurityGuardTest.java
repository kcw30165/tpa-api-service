package com.bct.ngtpa.apiservice.adapter.in.web.session.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.service.session.SessionCookieInstructionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class P4ASessionCookieSecurityGuardTest {

  private final HttpSessionCookieAdapter adapter = new HttpSessionCookieAdapter();
  private final SessionCookieInstructionFactory factory = new SessionCookieInstructionFactory();

  @Test
  void p4aWebCookieAdapterSetsAndClearsAuthFlowCookie() {
    var policy = authFlowCookiePolicy();

    var setResponse = new MockServerHttpResponse();
    adapter.apply(setResponse, factory.setCookie(policy, "afs_abc"));
    ResponseCookie setCookie = setResponse.getCookies().getFirst("auth_flow_sid");
    assertThat(setCookie).isNotNull();
    assertThat(setCookie.getValue()).isEqualTo("afs_abc");
    assertThat(setCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(setCookie.getMaxAge().getSeconds()).isEqualTo(1800);
    assertThat(setCookie.isHttpOnly()).isTrue();
    assertThat(setCookie.isSecure()).isTrue();
    assertThat(setCookie.getSameSite()).isEqualTo("None");

    var clearResponse = new MockServerHttpResponse();
    adapter.apply(clearResponse, factory.clearCookie(policy));
    ResponseCookie clearCookie = clearResponse.getCookies().getFirst("auth_flow_sid");
    assertThat(clearCookie).isNotNull();
    assertThat(clearCookie.getValue()).isEmpty();
    assertThat(clearCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(clearCookie.getMaxAge().getSeconds()).isZero();
    assertThat(clearCookie.isHttpOnly()).isTrue();
    assertThat(clearCookie.isSecure()).isTrue();
    assertThat(clearCookie.getSameSite()).isEqualTo("None");
  }

  @Test
  void p4aWebCookieAdapterSetsAndClearsXsrfCookieWithoutHttpOnly() {
    var policy = xsrfCookiePolicy();

    var setResponse = new MockServerHttpResponse();
    adapter.apply(setResponse, factory.setCookie(policy, "csrf_abc"));
    ResponseCookie setCookie = setResponse.getCookies().getFirst("XSRF-TOKEN");
    assertThat(setCookie).isNotNull();
    assertThat(setCookie.getValue()).isEqualTo("csrf_abc");
    assertThat(setCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(setCookie.getMaxAge().getSeconds()).isEqualTo(1800);
    assertThat(setCookie.isHttpOnly()).isFalse();
    assertThat(setCookie.isSecure()).isTrue();
    assertThat(setCookie.getSameSite()).isEqualTo("None");

    var clearResponse = new MockServerHttpResponse();
    adapter.apply(clearResponse, factory.clearCookie(policy));
    ResponseCookie clearCookie = clearResponse.getCookies().getFirst("XSRF-TOKEN");
    assertThat(clearCookie).isNotNull();
    assertThat(clearCookie.getValue()).isEmpty();
    assertThat(clearCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(clearCookie.getMaxAge().getSeconds()).isZero();
    assertThat(clearCookie.isHttpOnly()).isFalse();
    assertThat(clearCookie.isSecure()).isTrue();
    assertThat(clearCookie.getSameSite()).isEqualTo("None");
  }

  private static SessionCookiePolicy authFlowCookiePolicy() {
    return new SessionCookiePolicy(
        "auth_flow_sid", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE);
  }

  private static SessionCookiePolicy xsrfCookiePolicy() {
    return new SessionCookiePolicy(
        "XSRF-TOKEN", "/api/v1/auth", 1800, false, true, SessionCookieSameSite.NONE);
  }
}
