package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

public class AuthFlowCookieWriter {
  private final AuthFlowCookieProperties properties;
  private final boolean csrfCookieEnabled;

  public AuthFlowCookieWriter(AuthFlowCookieProperties properties) {
    this(properties, false);
  }

  public AuthFlowCookieWriter(AuthFlowCookieProperties properties, boolean csrfCookieEnabled) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.csrfCookieEnabled = csrfCookieEnabled;
  }

  public static AuthFlowCookieWriter from(AuthFlowSessionProperties authFlowSessionProperties) {
    Objects.requireNonNull(authFlowSessionProperties, "authFlowSessionProperties must not be null");
    var cookie = authFlowSessionProperties.getCookie();
    return new AuthFlowCookieWriter(
        new AuthFlowCookieProperties(
            cookie.getName(),
            cookie.getPath(),
            Duration.ofSeconds(cookie.getMaxAgeSeconds()),
            cookie.isHttpOnly(),
            cookie.isSecure(),
            cookie.getSameSite()),
        false);
  }

  /**
   * Test convenience factory. Production wiring must call {@link #from(AuthFlowSessionProperties)}.
   */
  public static AuthFlowCookieWriter defaultWriter() {
    return from(new AuthFlowSessionProperties());
  }

  public boolean csrfCookieEnabled() {
    return csrfCookieEnabled;
  }

  public ResponseCookie createAuthFlowCookie(String authFlowSessionId) {
    return ResponseCookie.from(properties.name(), authFlowSessionId)
        .path(properties.path())
        .maxAge(properties.maxAge())
        .httpOnly(properties.httpOnly())
        .secure(properties.secure())
        .sameSite(properties.sameSite())
        .build();
  }

  public void clearAuthFlowCookie(ServerHttpResponse response) {
    if (response == null) {
      return;
    }
    response.addCookie(
        ResponseCookie.from(properties.name(), "")
            .path(properties.path())
            .maxAge(Duration.ZERO)
            .httpOnly(properties.httpOnly())
            .secure(properties.secure())
            .sameSite(properties.sameSite())
            .build());
  }

  public void writeAuthFlowCookie(ServerHttpResponse response, String authFlowSessionId) {
    if (response != null && authFlowSessionId != null && !authFlowSessionId.isBlank()) {
      response.addCookie(createAuthFlowCookie(authFlowSessionId));
    }
  }
}
