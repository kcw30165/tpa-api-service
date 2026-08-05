package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfValidationStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StartForgotPasswordServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(5);
    private static final SessionTtlPolicy SESSION_TTL = new SessionTtlPolicy(true, true, 1800);
    private static final SessionConcurrencyPolicy CONCURRENCY = new SessionConcurrencyPolicy(
            SessionConcurrencyMode.SINGLE_ACTIVE_FLOW,
            SessionOnNewFlowPolicy.REPLACE_EXISTING);

    @Test
    void startsForgotPasswordFlowWithStartedSessionProtectedContextAndCsrf() {
        var fixture = new Fixture();

        StepVerifier.create(fixture.service.start(command(null)))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.STARTED);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.ENTER_ID);
                    assertThat(result.csrfToken()).isEqualTo("csrf-token-1");
                    assertThat(result.identityType()).isEqualTo("STAFF");
                    assertThat(result.authFlowSessionId()).isEqualTo("afs_fp_1");
                    assertThat(result.errors()).isEmpty();
                    assertThat(result.toString()).doesNotContain("member01", "tas-user-01", "cust-db01",
                            "csrf-token-1");
                })
                .verifyComplete();

        assertThat(fixture.lookup.calls).isEqualTo(1);
        assertThat(fixture.idempotency.completed).isTrue();
        assertThat(fixture.protector.lastContext.normalizedUsername()).isEqualTo("member01");
        assertThat(fixture.protector.lastContext.customerId()).isEqualTo("cust-db01");
        assertThat(fixture.protector.lastContext.tasUserId()).isEqualTo("tas-user-01");
        assertThat(fixture.protector.lastContext.publicIdentityType()).isEqualTo("STAFF_NO");
        assertThat(fixture.protector.lastContext.identityVerified()).isFalse();

        AuthFlowSession stored = fixture.sessions.session(new AuthFlowSessionId("afs_fp_1"));
        assertThat(stored.scenario()).isEqualTo(AuthFlowScenario.FORGOT_PASSWORD);
        assertThat(stored.status()).isEqualTo(AuthFlowStatus.STARTED);
        assertThat(stored.status()).isNotIn(
                AuthFlowStatus.IDENTITY_VERIFIED,
                AuthFlowStatus.RESET_AUTHORIZED,
                AuthFlowStatus.LOCKED,
                AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED);
        assertThat(stored.csrfTokenHash()).isNotBlank();
        assertThat(stored.data().maskedDestination()).isNull();
        assertThat(stored.data().attributes()).containsKeys(
                ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT,
                ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION,
                ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM);
    }

    @Test
    void newIdempotencyStartReplacesExistingActiveFlowAndReturnsNewSession() {
        var fixture = new Fixture();
        fixture.seedReplaySession("afs_existing", "old-csrf-token");

        StepVerifier.create(fixture.service.start(command("afs_existing")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.STARTED);
                    assertThat(result.authFlowSessionId()).isEqualTo("afs_fp_1");
                    assertThat(result.authFlowSessionId()).isNotEqualTo("afs_existing");
                    assertThat(result.csrfToken()).isEqualTo("csrf-token-1");
                })
                .verifyComplete();

        assertThat(fixture.sessions.session(new AuthFlowSessionId("afs_existing")).status())
                .isEqualTo(AuthFlowStatus.CANCELLED);
        assertThat(fixture.sessions.session(new AuthFlowSessionId("afs_fp_1")).status())
                .isEqualTo(AuthFlowStatus.STARTED);
        assertThat(fixture.sessions.creates).isEqualTo(1);
        assertThat(fixture.idempotency.completed).isTrue();
    }

    @Test
    void completedReplayDoesNotCallLookupOrCreateAnotherSessionAndReturnsFreshCsrf() {
        var fixture = new Fixture();
        fixture.seedReplaySession("afs_existing", "stale-csrf-token");
        fixture.idempotency.outcome = ForgotPasswordIdempotencyStartOutcome.COMPLETED_REPLAY;
        fixture.idempotency.record = fixture.idempotency.completedRecord("afs_existing");

        StepVerifier.create(fixture.service.start(command("afs_existing")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.COMPLETED_REPLAY);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.ENTER_ID);
                    assertThat(result.authFlowSessionId()).isEqualTo("afs_existing");
                    assertThat(result.csrfToken()).isEqualTo("csrf-token-1");
                    assertThat(result.identityType()).isEqualTo("STAFF");
                })
                .verifyComplete();

        assertThat(fixture.lookup.calls).isZero();
        assertThat(fixture.sessions.creates).isZero();

        StepVerifier.create(fixture.csrf.validateForSession(new ValidateAuthFlowSessionCsrfCommand(
                new AuthFlowSessionId("afs_existing"),
                "stale-csrf-token",
                CLOCK.instant(),
                null,
                null,
                java.util.List.of(),
                "req-1")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthFlowSessionCsrfValidationStatus.TOKEN_MISMATCH))
                .verifyComplete();

        StepVerifier.create(fixture.csrf.validateForSession(new ValidateAuthFlowSessionCsrfCommand(
                new AuthFlowSessionId("afs_existing"),
                "csrf-token-1",
                CLOCK.instant(),
                null,
                null,
                java.util.List.of(),
                "req-1")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(AuthFlowSessionCsrfValidationStatus.VALID))
                .verifyComplete();
    }

    @Test
    void revisionConflictWhileReplacingExistingSessionReturnsRestartFlowAndFailsIdempotency() {
        var fixture = new Fixture();
        fixture.seedReplaySession("afs_existing", "old-csrf-token");
        fixture.sessions.cancelRevisionOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;

        StepVerifier.create(fixture.service.start(command("afs_existing")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.AUTH_FLOW_INVALID);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
                    assertThat(result.authFlowSessionId()).isNull();
                    assertThat(result.csrfToken()).isNull();
                    assertThat(result.identityType()).isNull();
                    assertThat(result.errors()).singleElement().satisfies(error -> {
                        assertThat(error.code()).isEqualTo(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value());
                        assertThat(error.target()).isEqualTo("PAGE");
                    });
                })
                .verifyComplete();

        assertThat(fixture.idempotency.failed).isTrue();
        assertThat(fixture.sessions.creates).isZero();
    }

    private StartForgotPasswordCommand command(String existingAuthFlowSessionId) {
        return new StartForgotPasswordCommand(
                new ForgotPasswordIdempotencyKey("idem-fp-1"),
                " Member01 ",
                existingAuthFlowSessionId,
                "en",
                "req-1",
                IDEMPOTENCY_TTL);
    }

    private static final class Fixture {
        private final FakeForgotPasswordIdempotencyPort idempotency = new FakeForgotPasswordIdempotencyPort();
        private final FakeForgotPasswordUsernameLookupPort lookup = new FakeForgotPasswordUsernameLookupPort();
        private final FakeContextProtector protector = new FakeContextProtector();
        private final FakeAuthFlowSessionPort sessions = new FakeAuthFlowSessionPort();
        private final AuthFlowSessionCsrfService csrf;
        private final StartForgotPasswordService service;

        private Fixture() {
            var lifecycle = new AuthFlowSessionLifecycleService(
                    sessions,
                    new FixedSessionIdGenerator(),
                    new SessionTtlCalculator(),
                    CLOCK);
            var concurrency = new AuthFlowSessionConcurrencyService(lifecycle);
            this.csrf = new AuthFlowSessionCsrfService(sessions, new FixedCsrfTokenGenerator(),
                    new Sha256CsrfTokenHasher());
            this.service = new StartForgotPasswordService(
                    idempotency,
                    lookup,
                    protector,
                    concurrency,
                    csrf,
                    CLOCK,
                    SESSION_TTL,
                    CONCURRENCY,
                    "afs",
                    16,
                    true,
                    true,
                    true,
                    Map.of(
                            "GPN", RegistrationIdentityType.GPN,
                            "GPFS", RegistrationIdentityType.GPFS,
                            "STAFF", RegistrationIdentityType.STAFFNO,
                            "ID", RegistrationIdentityType.ID));
        }

        private void seedReplaySession(String authFlowSessionId, String csrfToken) {
            var now = CLOCK.instant();
            sessions.store(new AuthFlowSession(
                    new AuthFlowSessionId(authFlowSessionId),
                    AuthFlowScenario.FORGOT_PASSWORD,
                    AuthFlowStatus.STARTED,
                    new Sha256CsrfTokenHasher().hash(csrfToken),
                    null,
                    AuthFlowSessionData.empty(),
                    now,
                    now,
                    now.plusSeconds(1800)));
        }
    }

    private static final class FakeForgotPasswordIdempotencyPort implements ForgotPasswordIdempotencyPort {
        private ForgotPasswordIdempotencyStartOutcome outcome = ForgotPasswordIdempotencyStartOutcome.STARTED;
        private ForgotPasswordIdempotencyRecord record = processingRecord();
        private boolean completed;
        private boolean failed;

        @Override
        public Mono<ForgotPasswordIdempotencyStartResult> startProcessing(
                ForgotPasswordIdempotencyStartCommand command) {
            return Mono.just(new ForgotPasswordIdempotencyStartResult(outcome, record));
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markCompleted(ForgotPasswordIdempotencyCompleteCommand command) {
            completed = true;
            assertThat(command.replaySnapshot().safeHeaders()).doesNotContainKeys("Set-Cookie", "Cookie",
                    "X-XSRF-TOKEN");
            return Mono.just(completedRecord(command.replaySnapshot().opaqueCommitRef()));
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markFailed(ForgotPasswordIdempotencyFailCommand command) {
            failed = true;
            return Mono.just(processingRecord());
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markUnknownOutcome(
                ForgotPasswordIdempotencyUnknownOutcomeCommand command) {
            return Mono.just(processingRecord());
        }

        @Override
        public Mono<Optional<ForgotPasswordIdempotencyRecord>> load(ForgotPasswordIdempotencyAction action,
                ForgotPasswordIdempotencyKey key) {
            return Mono.just(Optional.of(record));
        }

        @Override
        public Mono<Void> delete(ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey key) {
            return Mono.empty();
        }

        private static ForgotPasswordIdempotencyRecord processingRecord() {
            return new ForgotPasswordIdempotencyRecord(
                    ForgotPasswordIdempotencyAction.START,
                    new ForgotPasswordIdempotencyKey("idem-fp-1"),
                    new ForgotPasswordRequestHash("hash-fp-1"),
                    ForgotPasswordIdempotencyStatus.PROCESSING,
                    null,
                    CLOCK.instant(),
                    CLOCK.instant(),
                    CLOCK.instant().plusSeconds(300));
        }

        private ForgotPasswordIdempotencyRecord completedRecord(String authFlowSessionId) {
            return new ForgotPasswordIdempotencyRecord(
                    ForgotPasswordIdempotencyAction.START,
                    new ForgotPasswordIdempotencyKey("idem-fp-1"),
                    new ForgotPasswordRequestHash("hash-fp-1"),
                    ForgotPasswordIdempotencyStatus.COMPLETED,
                    new ForgotPasswordIdempotencyReplaySnapshot("STARTED", authFlowSessionId, Map.of(), "STAFF"),
                    CLOCK.instant(),
                    CLOCK.instant(),
                    CLOCK.instant().plusSeconds(300));
        }
    }

    private static final class FakeForgotPasswordUsernameLookupPort implements ForgotPasswordUsernameLookupPort {
        private int calls;

        @Override
        public Mono<ForgotPasswordUsernameLookupResult> lookup(StartForgotPasswordUsernameLookupCommand command) {
            calls++;
            return Mono.just(ForgotPasswordUsernameLookupResult.confirmed(
                    "cust-db01",
                    "tas-user-01",
                    RegistrationIdentityType.STAFFNO,
                    "STAFF"));
        }
    }

    private static final class FakeContextProtector implements ForgotPasswordAuthFlowContextProtectorPort {
        private ForgotPasswordAuthFlowContext lastContext;

        @Override
        public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
            lastContext = context;
            return new ForgotPasswordAuthFlowEncryptedContext("ciphertext-1", "v1",
                    ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
        }

        @Override
        public ForgotPasswordAuthFlowContext unprotect(ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
        private final Map<String, AuthFlowSession> sessions = new LinkedHashMap<>();
        private int creates;
        private AuthFlowSessionMutationOutcome cancelRevisionOutcome = AuthFlowSessionMutationOutcome.APPLIED;

        @Override
        public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            creates++;
            var session = new AuthFlowSession(
                    command.authFlowSessionId(),
                    command.scenario(),
                    command.initialStatus(),
                    command.csrfTokenHash(),
                    command.activeOtpChallengeId(),
                    command.data(),
                    command.createdAt(),
                    command.createdAt(),
                    command.expiresAt());
            sessions.put(command.authFlowSessionId().value(), session);
            return Mono.just(session);
        }

        @Override
        public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
            return Mono.just(Optional.ofNullable(sessions.get(authFlowSessionId.value())));
        }

        @Override
        public Mono<AuthFlowSession> update(AuthFlowSession session) {
            sessions.put(session.authFlowSessionId().value(), session);
            return Mono.just(session);
        }

        @Override
        public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.just(session(authFlowSessionId));
        }

        @Override
        public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.just(session(authFlowSessionId));
        }

        @Override
        public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
            AuthFlowSession current = session(authFlowSessionId);
            var cancelled = new AuthFlowSession(
                    current.authFlowSessionId(),
                    current.scenario(),
                    AuthFlowStatus.CANCELLED,
                    current.csrfTokenHash(),
                    current.activeOtpChallengeId(),
                    current.data(),
                    current.createdAt(),
                    now,
                    current.expiresAt());
            sessions.put(authFlowSessionId.value(), cancelled);
            return Mono.just(cancelled);
        }

        @Override
        public Mono<AuthFlowSessionMutationResult> cancelRevisioned(
                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot expected,
                Instant now) {
            if (cancelRevisionOutcome != AuthFlowSessionMutationOutcome.APPLIED) {
                return Mono.just(AuthFlowSessionMutationResult.outcome(cancelRevisionOutcome));
            }
            return AuthFlowSessionPort.super.cancelRevisioned(expected, now);
        }

        @Override
        public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
            sessions.remove(authFlowSessionId.value());
            return Mono.empty();
        }

        private void store(AuthFlowSession session) {
            sessions.put(session.authFlowSessionId().value(), session);
        }

        private AuthFlowSession session(AuthFlowSessionId authFlowSessionId) {
            return sessions.get(authFlowSessionId.value());
        }
    }

    private static final class FixedSessionIdGenerator implements SessionIdGenerator {
        private int sequence;

        @Override
        public SessionId generate(SessionIdGenerationCommand command) {
            sequence++;
            return new SessionId("afs_fp_" + sequence);
        }
    }

    private static final class FixedCsrfTokenGenerator implements CsrfTokenGenerator {
        private int sequence;

        @Override
        public String generate() {
            sequence++;
            return "csrf-token-" + sequence;
        }
    }
}