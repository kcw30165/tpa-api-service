package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

/** Result status for authenticated-session lifecycle operations. */
public enum AuthenticatedSessionLifecycleStatus {
  ACTIVE,
  LOGGED_OUT,
  REVOKED,
  EXPIRED,
  NOT_FOUND,
  FAILED
}
