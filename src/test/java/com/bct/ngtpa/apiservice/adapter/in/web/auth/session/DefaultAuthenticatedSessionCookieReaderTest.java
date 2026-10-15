package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * Fail-first (RED) tests for {@link DefaultAuthenticatedSessionCookieReader}. The stub always
 * returns Optional.empty(), so the "valid cookie" case below is expected to FAIL until the next
 * script implements the real logic.
 */
class DefaultAuthenticatedSessionCookieReaderTest {

  private static final String COOKIE_NAME = "portal_sid";
  private static final String VALID_SESSION_ID = "sid_abcdefghijklmnopqrstuvwx";

  private AuthenticatedSessionCookieWriter writer() {
    AuthenticatedSessionCookieWriter w = mock(AuthenticatedSessionCookieWriter.class);
    when(w.cookieName()).thenReturn(COOKIE_NAME);
    return w;
  }

  @Test
  void returnsEmptyWhenCookieIsAbsent() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    var reader = new DefaultAuthenticatedSessionCookieReader(writer());

    assertThat(reader.read(exchange)).isEmpty();
  }

  @Test
  void returnsEmptyWhenCookieIsBlank() {
    var request =
        MockServerHttpRequest.get("/")
            .cookie(ResponseCookie.from(COOKIE_NAME, "").build())
            .build();
    var exchange = MockServerWebExchange.from(request);
    var reader = new DefaultAuthenticatedSessionCookieReader(writer());

    assertThat(reader.read(exchange)).isEmpty();
  }

  @Test
  void returnsEmptyWhenCookieIsMalformed() {
    var request =
        MockServerHttpRequest.get("/")
            .cookie(ResponseCookie.from(COOKIE_NAME, "not-a-valid-session-id!!").build())
            .build();
    var exchange = MockServerWebExchange.from(request);
    var reader = new DefaultAuthenticatedSessionCookieReader(writer());

    assertThat(reader.read(exchange)).isEmpty();
  }

  @Test
  void returnsParsedIdWhenCookieIsValid() {
    // EXPECTED TO FAIL against the stub (always returns empty).
    // This is the RED case the next script must turn GREEN.
    var request =
        MockServerHttpRequest.get("/")
            .cookie(ResponseCookie.from(COOKIE_NAME, VALID_SESSION_ID).build())
            .build();
    var exchange = MockServerWebExchange.from(request);
    var reader = new DefaultAuthenticatedSessionCookieReader(writer());

    assertThat(reader.read(exchange)).contains(new AuthenticatedSessionId(VALID_SESSION_ID));
  }
}
