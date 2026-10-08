package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup guard for the authenticated portal session cookie lifetime.
 *
 * <p>The server slides the idle window on every request, but the browser cookie carries a fixed
 * {@code Max-Age} set once at login. A cookie that expires before the absolute session limit makes
 * the browser drop {@code portal_sid} while the server-side session is still valid, so the sliding
 * window can never extend past the cookie lifetime. A cookie that outlives the absolute limit would
 * only leave a dead cookie in the browser.
 */
public final class AuthenticatedSessionCookieLifetimePolicy {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedSessionCookieLifetimePolicy.class);

  private AuthenticatedSessionCookieLifetimePolicy() {}

  /**
   * Rejects a cookie lifetime above the absolute session limit and warns when it is below it. A
   * non-positive absolute limit is left to the session lifecycle configuration to reject.
   */
  public static void requireWithinAbsoluteTimeout(
      long cookieMaxAgeSeconds, long absoluteTimeoutSeconds) {
    if (absoluteTimeoutSeconds <= 0) {
      return;
    }
    if (cookieMaxAgeSeconds > absoluteTimeoutSeconds) {
      throw new IllegalStateException(
          "auth-workflows.login.session.cookie.max-age-seconds ("
              + cookieMaxAgeSeconds
              + ") must not exceed auth-workflows.login.session.absolute-timeout-seconds ("
              + absoluteTimeoutSeconds
              + ")");
    }
    if (cookieMaxAgeSeconds < absoluteTimeoutSeconds) {
      LOG.warn(
          "event=authenticated.session.cookie.lifetime.shorter-than-absolute cookieMaxAgeSeconds={} absoluteTimeoutSeconds={}",
          cookieMaxAgeSeconds,
          absoluteTimeoutSeconds);
    }
  }
}
