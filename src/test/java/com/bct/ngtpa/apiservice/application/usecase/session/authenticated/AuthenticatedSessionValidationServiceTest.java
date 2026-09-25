package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedSessionValidationServiceTest {
  @Test
  void acceptsMatchingCsrfAndRejectsMismatch() {
    var hasher = new Sha256CsrfTokenHasher();
    var session =
        new AuthenticatedSession(
            new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
            new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
            List.of(),
            AuthenticatedSessionStatus.ACTIVE,
            hasher.hash("csrf_value"),
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(300),
            Instant.EPOCH.plusSeconds(900),
            new SessionRevision("0".repeat(32)),
            null);
    var service =
        new AuthenticatedSessionValidationService(
            command -> Mono.just(AuthenticatedSessionLifecycleResult.active(session)), hasher);
    StepVerifier.create(
            service.validate(
                new ValidateAuthenticatedSessionCommand(
                    session.authenticatedSessionId(), "csrf_value", "req")))
        .assertNext(value -> assertThat(value).isEqualTo(session))
        .verifyComplete();
    StepVerifier.create(
            service.validate(
                new ValidateAuthenticatedSessionCommand(
                    session.authenticatedSessionId(), "wrong", "req")))
        .expectError(AuthenticatedCsrfValidationException.class)
        .verify();
  }
}
