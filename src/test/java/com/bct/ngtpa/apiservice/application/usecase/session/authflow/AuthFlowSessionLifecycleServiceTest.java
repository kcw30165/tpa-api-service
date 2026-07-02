package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
    private final FakeAuthFlowSessionPort port = new FakeAuthFlowSessionPort();
    private final CapturingSessionIdGenerator sessionIdGenerator = new CapturingSessionIdGenerator();
    private final AuthFlowSessionLifecycleService service = new AuthFlowSessionLifecycleService(
            port,
            sessionIdGenerator,
            new SessionTtlCalculator(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startCreatesSessionWithGeneratedIdAndExpectedExpiry() {
        StepVerifier.create(service.start(startCommand("afs", 16, new SessionTtlPolicy(true, true, 1800))))
                .assertNext(result -> {
                    assertThat(result.session().authFlowSessionId().value()).startsWith("afs_");
                    assertThat(result.session().scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
                    assertThat(result.session().status()).isEqualTo(AuthFlowStatus.STARTED);
                    assertThat(result.session().createdAt()).isEqualTo(NOW);
                    assertThat(result.session().updatedAt()).isEqualTo(NOW);
                    assertThat(result.session().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
                    assertThat(result.expiresInSeconds()).isEqualTo(1800);
                })
                .verifyComplete();
        assertThat(port.createCount).isEqualTo(1);
        assertThat(sessionIdGenerator.lastCommand.prefix()).isEqualTo("afs");
        assertThat(sessionIdGenerator.lastCommand.entropyBytes()).isEqualTo(16);
    }

    @Test
    void startUsesSuppliedPrefixNotHardcodedAfs() {
        StepVerifier.create(service.start(startCommand("login", 16, new SessionTtlPolicy(true, true, 900))))
                .assertNext(result -> assertThat(result.session().authFlowSessionId().value()).startsWith("login_"))
                .verifyComplete();
    }

    @Test
    void startRejectsDisabledOrInvalidTtlPolicy() {
        StepVerifier.create(service.start(startCommand("afs", 16, new SessionTtlPolicy(false, true, 1800))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Auth flow TTL policy must be enabled"))
                .verify();
        assertThat(port.createCount).isZero();

        StepVerifier.create(service.start(startCommand("afs", 16, new SessionTtlPolicy(true, true, 0))))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(port.createCount).isZero();
    }

    @Test
    void loadReturnsExistingSession() {
        var session = activeSession(AuthFlowStatus.STARTED);
        port.save(session);

        StepVerifier.create(service.load(new LoadAuthFlowSessionCommand(session.authFlowSessionId(), "corr-1")))
                .expectNext(Optional.of(session))
                .verifyComplete();
    }

    @Test
    void loadReturnsEmptyWhenMissing() {
        StepVerifier.create(service.load(new LoadAuthFlowSessionCommand(new AuthFlowSessionId("afs_missing"), null)))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    void updateStatusUpdatesUpdatedAtAndPreservesExpiresAt() {
        var session = activeSession(AuthFlowStatus.STARTED);
        port.save(session);

        StepVerifier.create(service.updateStatus(new UpdateAuthFlowStatusCommand(session.authFlowSessionId(), AuthFlowStatus.OTP_SENT, null)))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(AuthFlowStatus.OTP_SENT);
                    assertThat(updated.updatedAt()).isEqualTo(NOW);
                    assertThat(updated.expiresAt()).isEqualTo(session.expiresAt());
                })
                .verifyComplete();
    }

    @Test
    void updateStatusRejectsTerminalSessionMutation() {
        for (AuthFlowStatus terminal : new AuthFlowStatus[]{AuthFlowStatus.COMPLETED, AuthFlowStatus.CANCELLED, AuthFlowStatus.EXPIRED}) {
            port.clear();
            var session = activeSession(terminal);
            port.save(session);
            StepVerifier.create(service.updateStatus(new UpdateAuthFlowStatusCommand(session.authFlowSessionId(), AuthFlowStatus.OTP_SENT, null)))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Auth flow session is not usable"))
                    .verify();
            assertThat(port.updateCount).isZero();
        }
    }

    @Test
    void updateActiveOtpChallengeStoresServerSideOtpReference() {
        var session = activeSession(AuthFlowStatus.OTP_SENT);
        port.save(session);

        StepVerifier.create(service.updateActiveOtpChallenge(new UpdateAuthFlowOtpCommand(session.authFlowSessionId(), "otp_abc", null)))
                .assertNext(updated -> {
                    assertThat(updated.activeOtpChallengeId()).isEqualTo("otp_abc");
                    assertThat(updated.updatedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void updateDestinationStoresMaskedDestinationOnly() {
        var session = activeSession(AuthFlowStatus.OTP_SENT);
        port.save(session);

        StepVerifier.create(service.updateDestination(new UpdateAuthFlowDestinationCommand(session.authFlowSessionId(), "EMAIL", "m****r@example.com", null)))
                .assertNext(updated -> {
                    assertThat(updated.data().contactType()).isEqualTo("EMAIL");
                    assertThat(updated.data().maskedDestination()).isEqualTo("m****r@example.com");
                    assertThat(recordComponentNames(AuthFlowSessionData.class)).doesNotContain("email", "mobile", "contactValue", "rawDestination");
                })
                .verifyComplete();
    }

    @Test
    void updateUsernameCheckStoresStateAndMovesToUsernameVerified() {
        var session = activeSession(AuthFlowStatus.OTP_VERIFIED);
        port.save(session);
        var state = usernameCheckState();

        StepVerifier.create(service.updateUsernameCheck(new UpdateAuthFlowUsernameCheckCommand(session.authFlowSessionId(), state, null)))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(AuthFlowStatus.USERNAME_VERIFIED);
                    assertThat(updated.data().usernameCheck()).isEqualTo(state);
                    assertThat(updated.updatedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void updateUsernameCheckRejectsSessionBeforeOtpVerified() {
        for (AuthFlowStatus status : new AuthFlowStatus[]{AuthFlowStatus.STARTED, AuthFlowStatus.OTP_SENT}) {
            port.clear();
            var session = activeSession(status);
            port.save(session);
            StepVerifier.create(service.updateUsernameCheck(new UpdateAuthFlowUsernameCheckCommand(session.authFlowSessionId(), usernameCheckState(), null)))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Auth flow session is not ready for username check"))
                    .verify();
            assertThat(port.sessions.get(session.authFlowSessionId().value()).data().usernameCheck()).isNull();
        }
    }

    @Test
    void completeMarksCompletedWithoutCreatingAuthenticatedSession() {
        var session = activeSession(AuthFlowStatus.USERNAME_VERIFIED);
        port.save(session);

        StepVerifier.create(service.complete(new CompleteAuthFlowSessionCommand(session.authFlowSessionId(), null)))
                .assertNext(completed -> assertThat(completed.status()).isEqualTo(AuthFlowStatus.COMPLETED))
                .verifyComplete();
    }

    @Test
    void cancelMarksCancelledWithoutClearingCookies() {
        var session = activeSession(AuthFlowStatus.OTP_SENT);
        port.save(session);

        StepVerifier.create(service.cancel(new CancelAuthFlowSessionCommand(session.authFlowSessionId(), "BACK", null)))
                .assertNext(cancelled -> assertThat(cancelled.status()).isEqualTo(AuthFlowStatus.CANCELLED))
                .verifyComplete();
        assertThat(port.cookieClearInstructionCreated).isFalse();
    }

    @Test
    void deleteDelegatesToPort() {
        var id = new AuthFlowSessionId("afs_delete");

        StepVerifier.create(service.delete(new DeleteAuthFlowSessionCommand(id, null))).verifyComplete();
        assertThat(port.deletedId).isEqualTo(id);
    }

    private static StartAuthFlowSessionCommand startCommand(String prefix, int entropy, SessionTtlPolicy ttlPolicy) {
        return new StartAuthFlowSessionCommand(
                AuthFlowScenario.REGISTRATION,
                AuthFlowStatus.STARTED,
                ttlPolicy,
                prefix,
                entropy,
                AuthFlowSessionData.empty(),
                null,
                null,
                "corr-1");
    }

    private static AuthFlowSession activeSession(AuthFlowStatus status) {
        return new AuthFlowSession(
                new AuthFlowSessionId("afs_existing"),
                AuthFlowScenario.REGISTRATION,
                status,
                null,
                null,
                AuthFlowSessionData.empty(),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800));
    }

    private static UsernameCheckState usernameCheckState() {
        return new UsernameCheckState(
                new SelectedTrust("TL", "ENV1", "Scheme A", "scheme-a", 10),
                "user_secret",
                NOW,
                NOW.plusSeconds(600));
    }

    private static java.util.List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static final class CapturingSessionIdGenerator implements SessionIdGenerator {
        private SessionIdGenerationCommand lastCommand;
        @Override
        public SessionId generate(SessionIdGenerationCommand command) {
            this.lastCommand = command;
            return new SessionId(command.prefix() + "_generated");
        }
    }

    private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
        private final Map<String, AuthFlowSession> sessions = new LinkedHashMap<>();
        private int createCount;
        private int updateCount;
        private AuthFlowSessionId deletedId;
        private boolean cookieClearInstructionCreated;

        void save(AuthFlowSession session) { sessions.put(session.authFlowSessionId().value(), session); }
        void clear() { sessions.clear(); createCount = 0; updateCount = 0; deletedId = null; cookieClearInstructionCreated = false; }
        @Override public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            createCount++;
            var session = new AuthFlowSession(command.authFlowSessionId(), command.scenario(), command.initialStatus(), command.csrfTokenHash(), command.activeOtpChallengeId(), command.data(), command.createdAt(), command.createdAt(), command.expiresAt());
            save(session);
            return Mono.just(session);
        }
        @Override public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) { return Mono.just(Optional.ofNullable(sessions.get(id.value()))); }
        @Override public Mono<AuthFlowSession> update(AuthFlowSession session) { updateCount++; save(session); return Mono.just(session); }
        @Override public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) { return Mono.error(new UnsupportedOperationException()); }
        @Override public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) {
            var existing = sessions.get(id.value());
            var updated = new AuthFlowSession(existing.authFlowSessionId(), existing.scenario(), AuthFlowStatus.COMPLETED, existing.csrfTokenHash(), existing.activeOtpChallengeId(), existing.data(), existing.createdAt(), now, existing.expiresAt());
            save(updated); return Mono.just(updated);
        }
        @Override public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
            var existing = sessions.get(id.value());
            var updated = new AuthFlowSession(existing.authFlowSessionId(), existing.scenario(), AuthFlowStatus.CANCELLED, existing.csrfTokenHash(), existing.activeOtpChallengeId(), existing.data(), existing.createdAt(), now, existing.expiresAt());
            save(updated); return Mono.just(updated);
        }
        @Override public Mono<Void> delete(AuthFlowSessionId id) { deletedId = id; sessions.remove(id.value()); return Mono.empty(); }
    }
}
