package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LogoutActiveAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LogoutAuthenticatedSessionUseCase;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Maps normal user logout onto the persisted ACTIVE to LOGGED_OUT lifecycle transition. */
public final class AuthenticatedLogoutService implements AuthenticatedLogoutUseCase {
  private final LogoutAuthenticatedSessionUseCase logout;

  public AuthenticatedLogoutService(LogoutAuthenticatedSessionUseCase logout) {
    this.logout = Objects.requireNonNull(logout, "logout must not be null");
  }

  @Override
  public Mono<AuthenticatedLogoutResult> logout(AuthenticatedLogoutCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return logout
        .logout(
            new LogoutActiveAuthenticatedSessionCommand(
                command.authenticatedSessionId(), command.correlationId()))
        .map(this::map)
        .onErrorReturn(AuthenticatedLogoutResult.failed());
  }

  private AuthenticatedLogoutResult map(AuthenticatedSessionLifecycleResult result) {
    if (result.status() == AuthenticatedSessionLifecycleStatus.LOGGED_OUT) {
      return result.session() == null
          ? AuthenticatedLogoutResult.alreadyLoggedOut()
          : AuthenticatedLogoutResult.loggedOut();
    }
    if (result.status() == AuthenticatedSessionLifecycleStatus.NOT_FOUND
        || result.status() == AuthenticatedSessionLifecycleStatus.EXPIRED
        || result.status() == AuthenticatedSessionLifecycleStatus.REVOKED) {
      return AuthenticatedLogoutResult.alreadyLoggedOut();
    }
    return AuthenticatedLogoutResult.failed();
  }
}
