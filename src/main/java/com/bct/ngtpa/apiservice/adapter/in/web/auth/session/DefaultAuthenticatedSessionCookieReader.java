package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Default implementation of {@link AuthenticatedSessionCookieReader}. */
@Component
public final class DefaultAuthenticatedSessionCookieReader
    implements AuthenticatedSessionCookieReader {

  private final AuthenticatedSessionCookieWriter sessionCookies;

  public DefaultAuthenticatedSessionCookieReader(AuthenticatedSessionCookieWriter sessionCookies) {
    this.sessionCookies = Objects.requireNonNull(sessionCookies);
  }

  @Override
  public Optional<AuthenticatedSessionId> read(ServerWebExchange exchange) {
    HttpCookie cookie =
        exchange.getRequest().getCookies().getFirst(sessionCookies.cookieName());
    if (cookie == null) {
      return Optional.empty();
    }
    String value = cookie.getValue();
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new AuthenticatedSessionId(value.trim()));
    } catch (RuntimeException malformed) {
      return Optional.empty();
    }
  }
}
