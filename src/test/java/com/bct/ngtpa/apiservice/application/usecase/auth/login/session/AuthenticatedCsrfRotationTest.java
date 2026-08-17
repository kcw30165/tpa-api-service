package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedCsrfRotationTest {
    @Test
    void persistsFreshAuthenticatedHashAndReturnsRawTokenOnce() {
        var createCommand = new AtomicReference<CreateAuthenticatedSessionCommand>();
        var sid = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
        var service = new EstablishAuthenticatedLoginSessionService(
                command -> {
                    createCommand.set(command);
                    var session = new AuthenticatedSession(
                            sid, command.actorContext(), command.accounts(), AuthenticatedSessionStatus.ACTIVE,
                            command.csrfTokenHash(), Instant.EPOCH, Instant.EPOCH,
                            Instant.EPOCH.plusSeconds(300), Instant.EPOCH.plusSeconds(900));
                    return Mono.just(AuthenticatedSessionLifecycleResult.active(session));
                },
                ignored -> Mono.empty(),
                ignored -> Mono.just(AuthenticatedSessionLifecycleResult.of(
                        AuthenticatedSessionLifecycleStatus.REVOKED)),
                () -> "fresh_authenticated_csrf",
                new Sha256CsrfTokenHasher());
        var account = new LoginSessionAccount(
                new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB",
                "00000000012",
                "0001",
                "TR",
                "OE",
                "ACTIVE",
                "MEMBER",
                null,
                null);
        var command = new EstablishAuthenticatedLoginSessionCommand(
                new AuthFlowSessionId("afs_opaque"),
                new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
                List.of(account),
                "pre_auth_csrf_hash_must_not_be_reused",
                "req");

        StepVerifier.create(service.establish(command))
                .assertNext(result -> {
                    assertThat(result.authenticatedSessionId()).isEqualTo(sid);
                    assertThat(result.authenticatedCsrfToken()).isEqualTo("fresh_authenticated_csrf");
                })
                .verifyComplete();

        var expectedHash = new Sha256CsrfTokenHasher().hash("fresh_authenticated_csrf");
        assertThat(createCommand.get().csrfTokenHash()).isEqualTo(expectedHash);
        assertThat(createCommand.get().csrfTokenHash())
                .isNotEqualTo("pre_auth_csrf_hash_must_not_be_reused");
    }
}
