package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EstablishAuthenticatedLoginSessionServiceTest {
    private static final AuthFlowSessionId AFS = new AuthFlowSessionId("afs_opaque");
    private static final AuthenticatedSessionId SID = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

    @Test
    void createsAuthenticatedSessionBeforeDeletingPreAuth() {
        var order = new ArrayList<String>();
        var service = service(order, false, false);
        StepVerifier.create(service.establish(command())).assertNext(r -> {
            assertThat(r.status()).isEqualTo(EstablishAuthenticatedLoginSessionStatus.ESTABLISHED);
            assertThat(r.authenticatedSessionId()).isEqualTo(SID);
            assertThat(r.clearAuthFlowCookie()).isTrue();
            assertThat(r.clearXsrfCookie()).isTrue();
            assertThat(r.authenticatedCsrfToken()).isEqualTo("fresh_authenticated_csrf");
        }).verifyComplete();
        assertThat(order).containsExactly("create-authenticated", "delete-preauth");
    }

    @Test
    void creationFailureKeepsPreAuthAndReturnsStableFailure() {
        var order = new ArrayList<String>();
        var service = service(order, true, false);
        StepVerifier.create(service.establish(command())).assertNext(
                r -> assertThat(r.status()).isEqualTo(EstablishAuthenticatedLoginSessionStatus.SESSION_CREATION_FAILED))
                .verifyComplete();
        assertThat(order).containsExactly("create-authenticated");
    }

    @Test
    void cleanupFailureRevokesNewSessionAndDoesNotExposeIt() {
        var order = new ArrayList<String>();
        var service = service(order, false, true);
        StepVerifier.create(service.establish(command())).assertNext(r -> {
            assertThat(r.status()).isEqualTo(EstablishAuthenticatedLoginSessionStatus.CLEANUP_FAILED);
            assertThat(r.authenticatedSessionId()).isNull();
            assertThat(r.clearAuthFlowCookie()).isFalse();
        }).verifyComplete();
        assertThat(order).containsExactly("create-authenticated", "delete-preauth", "revoke-authenticated");
    }

    private EstablishAuthenticatedLoginSessionService service(List<String> order, boolean createFail,
            boolean cleanupFail) {
        CreateAuthenticatedSessionUseCase create = c -> {
            order.add("create-authenticated");
            if (createFail)
                return Mono.error(new IllegalStateException("create failed"));
            var session = new AuthenticatedSession(SID, c.actorContext(), c.accounts(),
                    AuthenticatedSessionStatus.ACTIVE, c.csrfTokenHash(), Instant.EPOCH, Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(10), Instant.EPOCH.plusSeconds(20));
            return Mono.just(AuthenticatedSessionLifecycleResult.active(session));
        };
        DeleteAuthFlowSessionPort cleanup = id -> {
            order.add("delete-preauth");
            return cleanupFail ? Mono.error(new IllegalStateException("delete failed")) : Mono.empty();
        };
        RevokeAuthenticatedSessionUseCase revoke = c -> {
            order.add("revoke-authenticated");
            return Mono.just(AuthenticatedSessionLifecycleResult.of(AuthenticatedSessionLifecycleStatus.REVOKED));
        };
        return new EstablishAuthenticatedLoginSessionService(
                create,
                cleanup,
                revoke,
                () -> "fresh_authenticated_csrf",
                new com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher());
    }

    private EstablishAuthenticatedLoginSessionCommand command() {
        var account = new LoginSessionAccount(new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"), "DB", "8802",
                "00000008802", "1", "0001", "DBA", "OE", null, null);
        return new EstablishAuthenticatedLoginSessionCommand(AFS, new ActorContext("member", "USER"), List.of(account),
                "csrf_hash", "req");
    }
}
