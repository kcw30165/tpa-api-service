package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedLogoutServiceTest {
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void activeSessionTransitionReturnsLoggedOut() {
    AtomicReference<LogoutActiveAuthenticatedSessionCommand> seen = new AtomicReference<>();
    LogoutAuthenticatedSessionUseCase lifecycle =
        command -> {
          seen.set(command);
          return Mono.just(
              new AuthenticatedSessionLifecycleResult(
                  AuthenticatedSessionLifecycleStatus.LOGGED_OUT, loggedOutSession()));
        };
    var service = new AuthenticatedLogoutService(lifecycle);

    StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, "req-1")))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(AuthenticatedLogoutStatus.LOGGED_OUT))
        .verifyComplete();

    assertThat(seen.get().authenticatedSessionId()).isEqualTo(SID);
    assertThat(seen.get().correlationId()).isEqualTo("req-1");
  }

  @Test
  void missingExpiredRevokedOrAlreadyLoggedOutSessionIsIdempotentSuccess() {
    for (AuthenticatedSessionLifecycleStatus status :
        new AuthenticatedSessionLifecycleStatus[] {
          AuthenticatedSessionLifecycleStatus.NOT_FOUND,
          AuthenticatedSessionLifecycleStatus.EXPIRED,
          AuthenticatedSessionLifecycleStatus.REVOKED,
          AuthenticatedSessionLifecycleStatus.LOGGED_OUT
        }) {
      var service = service(status);
      StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, null)))
          .assertNext(
              result ->
                  assertThat(result.status())
                      .isEqualTo(AuthenticatedLogoutStatus.ALREADY_LOGGED_OUT))
          .verifyComplete();
    }
  }

  @Test
  void lifecycleFailureAndReactiveErrorReturnFailedWithoutLeakingCause() {
    StepVerifier.create(
            service(AuthenticatedSessionLifecycleStatus.FAILED)
                .logout(new AuthenticatedLogoutCommand(SID, "req-1")))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(AuthenticatedLogoutStatus.FAILED))
        .verifyComplete();

    var service =
        new AuthenticatedLogoutService(
            command -> Mono.error(new IllegalStateException("redis host secret")));
    StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, "req-1")))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(AuthenticatedLogoutStatus.FAILED);
              assertThat(result.toString()).doesNotContain("redis host secret", SID.value());
            })
        .verifyComplete();
  }

  private AuthenticatedLogoutService service(AuthenticatedSessionLifecycleStatus status) {
    return new AuthenticatedLogoutService(
        command -> Mono.just(AuthenticatedSessionLifecycleResult.of(status)));
  }

  private static AuthenticatedSession loggedOutSession() {
    Instant now = Instant.parse("2026-07-28T00:00:00Z");
    return new AuthenticatedSession(
        SID,
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.LOGGED_OUT,
        "csrf_hash",
        now.minusSeconds(60),
        now,
        now.plusSeconds(300),
        now.plusSeconds(300),
        new SessionRevision("0".repeat(32)),
        null);
  }
}
