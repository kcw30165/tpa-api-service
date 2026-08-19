package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import java.util.Objects;

public record AuthenticatedSessionLifecycleResult(
    AuthenticatedSessionLifecycleStatus status, AuthenticatedSession session) {
  public AuthenticatedSessionLifecycleResult {
    Objects.requireNonNull(status);
    if (status == AuthenticatedSessionLifecycleStatus.ACTIVE && session == null)
      throw new IllegalArgumentException("ACTIVE requires session");
  }

  public static AuthenticatedSessionLifecycleResult active(AuthenticatedSession s) {
    return new AuthenticatedSessionLifecycleResult(AuthenticatedSessionLifecycleStatus.ACTIVE, s);
  }

  public static AuthenticatedSessionLifecycleResult of(AuthenticatedSessionLifecycleStatus s) {
    return new AuthenticatedSessionLifecycleResult(s, null);
  }
}
