package com.bct.ngtpa.apiservice.adapter.out.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCancellationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionCancellationAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final AuthFlowSessionId ID = new AuthFlowSessionId("afs_safe");

    @Test
    void activeSession_isCancelledWithSuppliedTimestamp() {
        var sessions = new FakeSessions(session(AuthFlowStatus.COMPLETING));
        var adapter = new AuthFlowSessionCancellationAdapter(sessions);

        StepVerifier.create(adapter.cancel(ID,
                        AuthFlowSessionCancellationReason.REGISTRATION_MEMBER_NOT_FOUND, NOW))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(SessionCancellationStatus.CANCELLED))
                .verifyComplete();

        assertThat(sessions.cancelCalls).isEqualTo(1);
        assertThat(sessions.cancelledAt).isEqualTo(NOW);
    }

    @Test
    void alreadyCancelledSession_isIdempotent() {
        var sessions = new FakeSessions(session(AuthFlowStatus.CANCELLED));
        var adapter = new AuthFlowSessionCancellationAdapter(sessions);

        StepVerifier.create(adapter.cancel(ID,
                        AuthFlowSessionCancellationReason.USER_CANCELLED, NOW))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SessionCancellationStatus.ALREADY_CANCELLED);
                    assertThat(result.cancelled()).isTrue();
                })
                .verifyComplete();
        assertThat(sessions.cancelCalls).isZero();
    }

    @Test
    void completedOrExpiredSession_isNotRewrittenAsCancelled() {
        for (AuthFlowStatus status : new AuthFlowStatus[] {AuthFlowStatus.COMPLETED, AuthFlowStatus.EXPIRED}) {
            var sessions = new FakeSessions(session(status));
            var adapter = new AuthFlowSessionCancellationAdapter(sessions);
            StepVerifier.create(adapter.cancel(ID,
                            AuthFlowSessionCancellationReason.ADMINISTRATIVE, NOW))
                    .assertNext(result -> assertThat(result.status())
                            .isEqualTo(SessionCancellationStatus.ALREADY_TERMINAL))
                    .verifyComplete();
            assertThat(sessions.cancelCalls).isZero();
        }
    }

    @Test
    void missingSession_returnsNotFoundWithoutPersistenceMutation() {
        var sessions = new FakeSessions(null);
        var adapter = new AuthFlowSessionCancellationAdapter(sessions);

        StepVerifier.create(adapter.cancel(ID,
                        AuthFlowSessionCancellationReason.USER_CANCELLED, NOW))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(SessionCancellationStatus.NOT_FOUND))
                .verifyComplete();
        assertThat(sessions.cancelCalls).isZero();
    }

    @Test
    void cancellationInput_isRequired() {
        var adapter = new AuthFlowSessionCancellationAdapter(new FakeSessions(null));
        assertThatThrownBy(() -> adapter.cancel(null,
                AuthFlowSessionCancellationReason.USER_CANCELLED, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.cancel(ID, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.cancel(ID,
                AuthFlowSessionCancellationReason.USER_CANCELLED, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static AuthFlowSession session(AuthFlowStatus status) {
        return new AuthFlowSession(ID, AuthFlowScenario.REGISTRATION, status,
                "csrf-hash", null, new AuthFlowSessionData(null, null, Map.of()),
                NOW.minusSeconds(60), NOW.minusSeconds(30), NOW.plusSeconds(1800));
    }

    private static final class FakeSessions implements AuthFlowSessionPort {
        private final AuthFlowSession session;
        private int cancelCalls;
        private Instant cancelledAt;
        FakeSessions(AuthFlowSession session) { this.session = session; }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
            return Mono.just(Optional.ofNullable(session));
        }
        @Override public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
            cancelCalls++; cancelledAt = now; return Mono.just(session(AuthFlowStatus.CANCELLED));
        }
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<AuthFlowSession> update(AuthFlowSession session) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<Void> delete(AuthFlowSessionId id) { return Mono.empty(); }
    }
}
