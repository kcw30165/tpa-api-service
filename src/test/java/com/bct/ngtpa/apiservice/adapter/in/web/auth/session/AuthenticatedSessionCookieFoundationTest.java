package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class AuthenticatedSessionCookieFoundationTest {
  @Test
  void portalSessionCookieIsTheOnlyAuthenticatedCookie() {
    var response = new MockServerHttpResponse();
    new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie())
        .write(response, "sid_abcdefghijklmnopqrstuvwx");
    var portal = response.getCookies().getFirst("portal_sid");
    assertThat(portal).isNotNull();
    assertThat(portal.isHttpOnly()).isTrue();
    assertThat(portal.getPath()).isEqualTo("/api");
    assertThat(response.getCookies()).doesNotContainKey("XSRF-TOKEN");
  }

  @Test
  void clearExpiresOnlyPortalSessionCookie() {
    var response = new MockServerHttpResponse();
    new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie())
        .clear(response);
    assertThat(response.getCookies().getFirst("portal_sid").getMaxAge().isZero()).isTrue();
    assertThat(response.getCookies()).doesNotContainKey("XSRF-TOKEN");
  }
}
