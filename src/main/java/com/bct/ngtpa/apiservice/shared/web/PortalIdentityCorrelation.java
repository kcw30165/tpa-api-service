package com.bct.ngtpa.apiservice.shared.web;

/**
 * Reactor Context keys carrying already-derived portal identity values as plain Strings.
 *
 * <p>Outbound adapters must never read identity objects. Inbound filters derive these Strings once
 * and publish them here, mirroring the established {@link RequestCorrelation} request-id contract.
 */
public final class PortalIdentityCorrelation {

  /** Reactor Context key holding the selected account trust code, or an empty String. */
  public static final String TRUST_CODE_CONTEXT_KEY =
      PortalIdentityCorrelation.class.getName() + ".trustCode";

  /** Reactor Context key holding the acting user role, or an empty String. */
  public static final String USER_ROLE_CONTEXT_KEY =
      PortalIdentityCorrelation.class.getName() + ".userRole";

  private PortalIdentityCorrelation() {}
}
