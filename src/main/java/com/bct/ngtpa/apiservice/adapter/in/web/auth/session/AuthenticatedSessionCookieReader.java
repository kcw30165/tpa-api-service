package com.bct.ngtpa.apiservice.adapter.in.web.auth.session;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

/**
 * Centralized reader for the authenticated session cookie.
 *
 * <p>Consolidates the "read portal_sid cookie -> construct AuthenticatedSessionId" concern that was
 * previously duplicated independently by LogoutController, PortalAccessContextWebFilter, and
 * FundPriceController. This is the first consumer-facing addition; migrating those three existing
 * call sites onto this reader is an explicitly deferred follow-up, not part of this change.
 */
public interface AuthenticatedSessionCookieReader {

  /**
   * Reads the authenticated session cookie from the exchange and parses it.
   *
   * @return the parsed {@link AuthenticatedSessionId}, or {@link Optional#empty()} if the cookie is
   *     absent, blank, or fails {@link AuthenticatedSessionId}'s format validation. Callers decide
   *     how to signal failure (e.g. 401, idempotent success), matching each call site's existing
   *     convention.
   */
  Optional<AuthenticatedSessionId> read(ServerWebExchange exchange);
}
