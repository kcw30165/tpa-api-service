package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult.Outcome;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;

import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.SecureRandomCsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the Task 019 {@link StartUsernameRecoveryService} orchestration: validation, START
 * idempotency reservation and distinct classification, one-time VERIFIED captcha consumption,
 * lookup ordering, zero-registered no-session behavior, registered minimal protected-session
 * creation with opaque Scheme-Refs and hashed CSRF, safe replay completion, and sanitized
 * failure mapping.
 *
 * <p>Design references: {@code .cline/tasks/019-start-use-case.md} required orchestration order
 * and {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
 * (UR-START-001..015).
 */
class StartUsernameRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int TTL_SECONDS = 600;
    private static final String CAPTCHA = "cap_f67f65980e944119b8b81c74d9a1834a";
    private static final String IDEM_KEY = "idem_12345678";
    private static final String EMAIL = "hullcs.qglybcy@uc.kji";
    private static final Locale LANGUAGE = Locale.ENGLISH;

    private final List<String> callLog = new ArrayList<>();
    private final FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort(callLog);
    private final FakeCaptchaChallengePort captchaPort =
            new FakeCaptchaChallengePort(callLog, challenge(CaptchaStatus.VERIFIED, NOW.plusSeconds(300)));
    private final FakeLookupPort lookupPort = new FakeLookupPort(callLog);
    private final FakeSessionPort sessionPort = new FakeSessionPort(callLog);

    private final RegistrationCaptchaGateService captchaGateService =
            new RegistrationCaptchaGateService(captchaPort, CLOCK);
    private final CsrfTokenGenerator csrfTokenGenerator = new SecureRandomCsrfTokenGenerator();
    private final CsrfTokenHasher csrfTokenHasher = new Sha256CsrfTokenHasher();

    private StartUsernameRecoveryService service;

    @BeforeEach
    void setUp() {
        lookupPort.result = matchedLookup();
        service = new StartUsernameRecoveryService(
                new UsernameRecoveryStartValidator(),
                new UsernameRecoveryStartFingerprintGenerator(),
                idempotencyPort,
                captchaGateService,
                lookupPort,
                sessionPort,
                csrfTokenGenerator,
                csrfTokenHasher,
                CLOCK,
                TTL_SECONDS);
    }

    @Test
    void invalidInputMapsToTypedValidationFailureWithoutSideEffects() {
        StepVerifier.create(service.start(new UsernameRecoveryStartCommand(
                        MemberIdentityType.STAFFNO, "3030609", "04/07/1978", null, null, CAPTCHA, LANGUAGE, IDEM_KEY)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.VALIDATION_FAILED);
                })
                .verify();

        assertThat(callLog).isEmpty();
    }

    @Test
    void unsafeIdempotencyKeyMapsToTypedValidationFailureBeforeReservation() {
        StepVerifier.create(service.start(command(EMAIL, null, CAPTCHA, "csrf_bad_key")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.VALIDATION_FAILED);
                })
                .verify();

        assertThat(callLog).isEmpty();
    }

    @Test
    void reservesStartWithSemanticFingerprintBeforeCaptchaConsumption() {
        UsernameRecoveryStartCommand command = emailCommand(EMAIL);

        StepVerifier.create(service.start(command))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(callLog).startsWith("idem.reserve");
        UsernameRecoveryIdempotencyPort.ReserveCommand reserve = idempotencyPort.lastReserveCommand;
        assertThat(reserve.action()).isEqualTo(UsernameRecoveryIdempotencyAction.START);
        assertThat(reserve.idempotencyKey().value()).isEqualTo(IDEM_KEY);
        assertThat(reserve.fingerprint()).isEqualTo(
                new UsernameRecoveryStartFingerprintGenerator().derive(command));
        assertThat(reserve.operationReference().value()).isNotBlank();
        assertThat(reserve.now()).isEqualTo(NOW);
        assertThat(reserve.ttl().toSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    void duplicateInProgressMapsToTypedInProgressSignalWithoutSideEffects() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void keyReusedMapsToTypedKeyReusedSignal() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.IDEMPOTENCY_KEY_REUSED);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void unknownOutcomeExistingMapsToTypedUnknownOutcomeSignal() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.UNKNOWN_OUTCOME_EXISTING);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void reserveSystemFailureOutcomeMapsToTypedSystemFailure() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.SYSTEM_FAILURE;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void reserveInfrastructureErrorMapsToTypedSystemFailure() {
        idempotencyPort.reserveFails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void completedSessionReplayRotatesCsrfFromLoadedSession() {
        UsernameRecoverySession stored = activeSession();
        sessionPort.store(stored);
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryStartReplayCodec.encode(stored.sessionId()));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(Outcome.SESSION_CREATED);
                    assertThat(result.csrfToken()).isNotBlank();
                    assertThat(result.expiresInSeconds()).isEqualTo(600);
                    assertThat(result.schemes()).hasSize(2);
                    assertThat(result.schemes().get(0).registered()).isTrue();
                    assertThat(result.schemes().get(1).registered()).isFalse();
                    assertThat(result.schemes()).allSatisfy(projection ->
                            assertThat(projection.schemeRef()).isNotNull());
                    assertThat(new Sha256CsrfTokenHasher().hash(result.csrfToken()))
                            .isEqualTo(sessionPort.lastMutation.updatedSession().csrfHash().value());
                    assertThat(sessionPort.lastMutation.updatedSession().csrfHash().value())
                            .isNotEqualTo(stored.csrfHash().value());
                    assertThat(sessionPort.lastMutation.updatedSession().expiresAt())
                            .isEqualTo(stored.expiresAt());
                })
                .verifyComplete();

        assertThat(callLog).containsExactly(
                "idem.reserve", "idem.resolve", "session.load", "session.update");
        assertThat(captchaPort.markConsumedCalls).isZero();
        assertThat(lookupPort.searchCalls).isZero();
        assertThat(sessionPort.createCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void completedSessionReplayRevisionConflictIsDistinctAndNotRetried() {
        UsernameRecoverySession stored = activeSession();
        sessionPort.store(stored);
        sessionPort.updateOutcome = UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT;
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryStartReplayCodec.encode(stored.sessionId()));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.REPLAY_REVISION_CONFLICT);
                })
                .verify();

        assertThat(sessionPort.updateCalls).isEqualTo(1);
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void completedNonSessionReplayReturnsOriginalResultWithoutReExecuting() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        UsernameRecoveryStartResult original = new UsernameRecoveryStartResult(
                Outcome.MEMBER_NOT_FOUND, null, null, List.of());
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryStartReplaySnapshot.encode(original));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> assertThat(result).isEqualTo(original))
                .verifyComplete();

        assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
        assertThat(captchaPort.markConsumedCalls).isZero();
        assertThat(lookupPort.searchCalls).isZero();
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void corruptReplaySnapshotFailsClosedAsSystemFailure() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("corrupt-payload"));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void replayWithMissingRecordFailsClosedAsSystemFailure() {
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = null;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void replayWithExpiredSessionFailsClosedWithoutRotation() {
        UsernameRecoverySession stored = activeSession();
        UsernameRecoverySession expired = new UsernameRecoverySession(
                stored.sessionId(), stored.scenario(), stored.status(), stored.revision(),
                stored.csrfHash(), stored.destination(), stored.language(), NOW.minusSeconds(10),
                NOW.minusSeconds(1), stored.candidates());
        sessionPort.store(expired);
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryStartReplayCodec.encode(expired.sessionId()));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.updateCalls).isZero();
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void replayWithAbsentSessionFailsClosedWithoutBlindCreation() {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId("afs_gone");
        sessionPort.stored = null;
        idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
        idempotencyPort.storedRecord = completedRecord(
                UsernameRecoveryStartReplayCodec.encode(sessionId));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.createCalls).isZero();
    }


    @Test
    void captchaNotVerifiedRejectsBeforeLookup() {
        captchaPort.challenge = challenge(CaptchaStatus.CREATED, NOW.plusSeconds(300));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.CAPTCHA_NOT_VERIFIED);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "captcha.load");
        assertThat(lookupPort.searchCalls).isZero();
        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void captchaNotFoundMapsToTypedNotFoundSignal() {
        captchaPort.challenge = null;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.CAPTCHA_NOT_FOUND);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "captcha.load");
    }

    @Test
    void captchaConflictMapsToTypedConflictSignal() {
        captchaPort.challenge = challenge(CaptchaStatus.CONSUMED, NOW.plusSeconds(300));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.CAPTCHA_CONFLICT);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "captcha.load");
        assertThat(captchaPort.markConsumedCalls).isZero();
    }

    @Test
    void captchaExpiredMapsToTypedExpiredSignal() {
        captchaPort.challenge = challenge(CaptchaStatus.VERIFIED, NOW.minusSeconds(1));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.CAPTCHA_EXPIRED);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve", "captcha.load");
        assertThat(captchaPort.markConsumedCalls).isZero();
    }

    @Test
    void malformedCaptchaIdMapsToCaptchaInvalidBeforeConsumption() {
        StepVerifier.create(service.start(command(EMAIL, null, "not-a-challenge-id", IDEM_KEY)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.CAPTCHA_INVALID);
                })
                .verify();

        assertThat(callLog).containsExactly("idem.reserve");
    }

    @Test
    void lookupRunsOnlyAfterReservationAndVerifiedCaptchaConsumption() {
        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(callLog).startsWith(
                "idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
        assertThat(captchaPort.markConsumedCalls).isEqualTo(1);
        assertThat(lookupPort.searchCalls).isEqualTo(1);
    }

    @Test
    void sessionCreatedResultCarriesExactGeneratedAuthFlowSessionId() {
        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(
                            UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
                    assertThat(result.authFlowSessionId()).isNotNull();
                    assertThat(sessionPort.stored).isNotNull();
                    assertThat(result.authFlowSessionId().value())
                            .isEqualTo(sessionPort.stored.sessionId().value());
                    assertThat(result.csrfToken()).isNotBlank();
                })
                .verifyComplete();

        assertThat(sessionPort.createCalls).isEqualTo(1);
    }

    @Test
    void nonSessionOutcomesCarryNoSessionIdOrRawCsrf() {
        lookupPort.result = new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(
                            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
                    assertThat(result.authFlowSessionId()).isNull();
                    assertThat(result.csrfToken()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(sessionPort.createCalls).isZero();
    }



    @Test
    void memberNotFoundReturnsTypedResultAndCompletesIdempotency() {
        lookupPort.result = new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(Outcome.MEMBER_NOT_FOUND);
                    assertThat(result.schemes()).isEmpty();
                    assertThat(result.csrfToken()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(callLog).containsExactly(
                "idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search", "idem.complete");
        assertThat(sessionPort.createCalls).isZero();
        UsernameRecoveryStartReplaySnapshot.DecodeResult decoded = UsernameRecoveryStartReplaySnapshot.decode(
                idempotencyPort.lastCompleted.replaySnapshot());
        assertThat(decoded.decoded()).isTrue();
        assertThat(decoded.result().outcome()).isEqualTo(Outcome.MEMBER_NOT_FOUND);
    }

    @Test
    void zeroRegisteredCreatesNoSessionAndReturnsRegistrationGuidance() {
        lookupPort.result = new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.MATCHED, List.of(
                        new UsernameRecoveryLookupCandidate("DB", "DBA", false, null)));

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(Outcome.ZERO_REGISTERED);
                    assertThat(result.schemes()).hasSize(1);
                    assertThat(result.schemes().get(0).registered()).isFalse();
                    assertThat(result.schemes().get(0).schemeRef()).isNull();
                    assertThat(result.csrfToken()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(callLog).containsExactly(
                "idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search", "idem.complete");
        assertThat(sessionPort.createCalls).isZero();
        UsernameRecoveryStartReplaySnapshot.DecodeResult decoded = UsernameRecoveryStartReplaySnapshot.decode(
                idempotencyPort.lastCompleted.replaySnapshot());
        assertThat(decoded.decoded()).isTrue();
        assertThat(decoded.result().outcome()).isEqualTo(Outcome.ZERO_REGISTERED);
        assertThat(decoded.result().schemes().get(0).trustCode()).isEqualTo("DBA");
    }

    @Test
    void lookupContractInvalidMapsToSystemFailureWithoutCompletion() {
        lookupPort.result = new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.CONTRACT_INVALID, List.of());

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly(
                "idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
        assertThat(idempotencyPort.completeCalls).isZero();
        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void lookupInfrastructureExceptionMapsToSystemFailure() {
        lookupPort.fails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(callLog).containsExactly(
                "idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
        assertThat(idempotencyPort.completeCalls).isZero();
    }


    @Test
    void registeredCandidatesCreateMinimalSessionWithOpaqueSchemeRefsAndHashedCsrf() {
        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(Outcome.SESSION_CREATED);
                    assertThat(result.csrfToken()).isNotBlank();
                    assertThat(result.expiresInSeconds()).isEqualTo(TTL_SECONDS);
                    assertThat(result.schemes()).hasSize(2);
                    assertThat(result.schemes().get(0).registered()).isTrue();
                    assertThat(result.schemes().get(1).registered()).isFalse();
                    assertThat(result.schemes()).allSatisfy(projection ->
                            assertThat(projection.schemeRef()).isNotNull());
                })
                .verifyComplete();

        assertThat(callLog).containsExactly(
                "idem.reserve", "captcha.load", "captcha.markConsumed",
                "lookup.search", "session.create", "idem.complete");

        UsernameRecoverySession stored = sessionPort.stored;
        assertThat(stored).isNotNull();
        assertThat(stored.scenario()).isEqualTo(UsernameRecoverySession.Scenario.USERNAME_RECOVERY);
        assertThat(stored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
        assertThat(stored.revision().value()).isZero();
        assertThat(stored.language()).isEqualTo(LANGUAGE);
        assertThat(stored.destination()).isEqualTo(new UsernameRecoveryVerifiedDestination(
                EMAIL, UsernameRecoveryVerifiedDestination.Channel.EMAIL));
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.expiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
        assertThat(stored.candidates()).hasSize(2);
        assertThat(stored.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.AVAILABLE);
            assertThat(candidate.schemeRef()).isNotNull();
        });
        assertThat(stored.candidates().get(0).userName()).isEqualTo("user-1");
        assertThat(stored.candidates().get(1).userName()).isNull();

        UsernameRecoveryStartReplayCodec.DecodeResult decoded = UsernameRecoveryStartReplayCodec.decode(
                idempotencyPort.lastCompleted.replaySnapshot());
        assertThat(decoded.decoded()).isTrue();
        assertThat(decoded.sessionId()).isEqualTo(stored.sessionId());
    }

    @Test
    void sessionCreateInfrastructureErrorMapsToSystemFailure() {
        sessionPort.createFails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void sessionCreateSystemFailureOutcomeMapsToSystemFailure() {
        sessionPort.createOutcome = UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(idempotencyPort.completeCalls).isZero();
    }

    @Test
    void completeFailureAfterSessionCreateMapsToSystemFailureWithoutSuccess() {
        idempotencyPort.completeFails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.createCalls).isEqualTo(1);
        assertThat(callLog).contains("session.create");
    }


    @Test
    void completeFailureAfterMemberNotFoundMapsToSystemFailure() {
        lookupPort.result = new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());
        idempotencyPort.completeFails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(sessionPort.createCalls).isZero();
    }

    @Test
    void captchaRemainsConsumedWhenLookupFails() {
        lookupPort.fails = true;

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
                    assertThat(((UsernameRecoveryStartException) error).reason())
                            .isEqualTo(Reason.SYSTEM_FAILURE);
                })
                .verify();

        assertThat(captchaPort.markConsumedCalls).isEqualTo(1);
    }

    @Test
    void resultToStringNeverDisclosesCsrfOrSensitiveValues() {
        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .assertNext(result -> {
                    assertThat(result.toString()).doesNotContain(result.csrfToken());
                    assertThat(result.toString()).doesNotContain(EMAIL);
                    assertThat(result.toString()).doesNotContain("3030609");
                    assertThat(result.toString()).doesNotContain("04/07/1978");
                })
                .verifyComplete();
    }

    @Test
    void reactiveProgrammingErrorPropagatesToCentralBoundary() {
        idempotencyPort.reserveError = new AssertionError("reactive defect");

        StepVerifier.create(service.start(emailCommand(EMAIL)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AssertionError.class)
                        .hasMessage("reactive defect"))
                .verify();
    }

    @Test
    void unexpectedProgrammingDefectPropagatesToCentralBoundary() {
        UsernameRecoveryIdempotencyPort defective = new UsernameRecoveryIdempotencyPort() {
            @Override
            public Mono<ReservationResult> reserve(ReserveCommand command) {
                throw new AssertionError("defect");
            }

            @Override
            public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
                    UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey key) {
                return Mono.error(new AssertionError("defect"));
            }

            @Override
            public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command) {
                return Mono.error(new AssertionError("defect"));
            }

            @Override
            public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand command) {
                return Mono.error(new AssertionError("defect"));
            }
        };
        StartUsernameRecoveryService defectiveService = new StartUsernameRecoveryService(
                new UsernameRecoveryStartValidator(),
                new UsernameRecoveryStartFingerprintGenerator(),
                defective,
                captchaGateService,
                lookupPort,
                sessionPort,
                csrfTokenGenerator,
                csrfTokenHasher,
                CLOCK,
                TTL_SECONDS);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                defectiveService.start(emailCommand(EMAIL)))
                .isInstanceOf(AssertionError.class)
                .hasMessage("defect");
    }


    private static UsernameRecoveryStartCommand emailCommand(String email) {
        return new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978", email, null, CAPTCHA, LANGUAGE, IDEM_KEY);
    }

    private static UsernameRecoveryStartCommand mobileCommand(String mobile) {
        return new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978", null, mobile, CAPTCHA, LANGUAGE, IDEM_KEY);
    }

    private static UsernameRecoveryStartCommand command(
            String email, String mobile, String captchaChallengeId, String idempotencyKey) {
        return new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978", email, mobile,
                captchaChallengeId, LANGUAGE, idempotencyKey);
    }

    private static UsernameRecoveryLookupResult matchedLookup() {
        return new UsernameRecoveryLookupResult(
                UsernameRecoveryLookupResult.Status.MATCHED, List.of(
                        new UsernameRecoveryLookupCandidate("DB", "DBA", true, "user-1"),
                        new UsernameRecoveryLookupCandidate("DB", "DBB", false, null)));
    }

    private static CaptchaChallenge challenge(CaptchaStatus status, Instant expiresAt) {
        return new CaptchaChallenge(
                new CaptchaChallengeId(CAPTCHA),
                new CaptchaAnswerHash(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                status,
                0,
                5,
                NOW.minusSeconds(60),
                expiresAt,
                status == CaptchaStatus.VERIFIED ? NOW.minusSeconds(30) : null,
                null,
                null,
                null);
    }

    private static UsernameRecoverySession activeSession() {
        UsernameRecoverySessionId sessionId = new UsernameRecoverySessionId("afs_opaque-1");
        UsernameRecoverySchemeRef registeredRef = new UsernameRecoverySchemeRef("sch_opaque-1");
        UsernameRecoverySchemeRef unregisteredRef = new UsernameRecoverySchemeRef("sch_opaque-2");
        List<UsernameRecoverySessionCandidate> candidates = List.of(
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(registeredRef, "DB", "DBA", true, "user-1",
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null),
                new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(unregisteredRef, "DB", "DBB", false, null,
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null));
        return new UsernameRecoverySession(
                sessionId,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash(new Sha256CsrfTokenHasher().hash("original-token")),
                new UsernameRecoveryVerifiedDestination(EMAIL, UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                LANGUAGE,
                NOW,
                NOW.plusSeconds(TTL_SECONDS),
                candidates);
    }

    private static UsernameRecoveryIdempotencyRecord completedRecord(
            UsernameRecoveryIdempotencyRecord.ReplaySnapshot snapshot) {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.START,
                new UsernameRecoveryIdempotencyKey(IDEM_KEY),
                new UsernameRecoveryIdempotencyFingerprint(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                new UsernameRecoveryIdempotencyOperationReference("op-ref"),
                UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                snapshot,
                NOW,
                NOW,
                NOW.plusSeconds(TTL_SECONDS));
    }


    private static final class FakeIdempotencyPort implements UsernameRecoveryIdempotencyPort {

        private final List<String> callLog;
        private UsernameRecoveryIdempotencyOutcome reserveOutcome = UsernameRecoveryIdempotencyOutcome.RESERVED;
        private boolean reserveFails;
        private Throwable reserveError;
        private boolean completeFails;
        private UsernameRecoveryIdempotencyRecord storedRecord;
        private UsernameRecoveryIdempotencyPort.ReserveCommand lastReserveCommand;
        private UsernameRecoveryIdempotencyPort.CompleteCommand lastCompleted;
        private int completeCalls;

        private FakeIdempotencyPort(List<String> callLog) {
            this.callLog = callLog;
        }

        @Override
        public Mono<ReservationResult> reserve(ReserveCommand command) {
            callLog.add("idem.reserve");
            lastReserveCommand = command;
            if (reserveError != null) {
                return Mono.error(reserveError);
            }
            if (reserveFails) {
                return Mono.error(new IllegalStateException("idempotency reserve infrastructure failure"));
            }
            if (reserveOutcome == UsernameRecoveryIdempotencyOutcome.RESERVED) {
                UsernameRecoveryIdempotencyReservation reservation = new UsernameRecoveryIdempotencyReservation(
                        command.action(), command.idempotencyKey(), command.fingerprint(),
                        command.operationReference(), command.expiresAt());
                return Mono.just(new ReservationResult(reserveOutcome, reservation));
            }
            return Mono.just(new ReservationResult(reserveOutcome, null));
        }

        @Override
        public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
                UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey idempotencyKey) {
            callLog.add("idem.resolve");
            if (storedRecord == null) {
                return Mono.just(Optional.empty());
            }
            return Mono.just(Optional.of(storedRecord));
        }

        @Override
        public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command) {
            callLog.add("idem.complete");
            completeCalls++;
            lastCompleted = command;
            if (completeFails) {
                return Mono.error(new IllegalStateException("idempotency complete infrastructure failure"));
            }
            return Mono.just(new UsernameRecoveryIdempotencyRecord(
                    command.action(), command.idempotencyKey(), command.fingerprint(),
                    command.operationReference(), UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
                    command.replaySnapshot(), command.now(), command.now(), command.now().plus(command.ttl())));
        }

        @Override
        public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(UnknownOutcomeCommand command) {
            throw new UnsupportedOperationException("start does not record unknown outcomes");
        }
    }

    private static final class FakeCaptchaChallengePort implements CaptchaChallengePort {

        private final List<String> callLog;
        private CaptchaChallenge challenge;
        private int markConsumedCalls;

        private FakeCaptchaChallengePort(List<String> callLog, CaptchaChallenge challenge) {
            this.callLog = callLog;
            this.challenge = challenge;
        }

        @Override
        public Mono<CaptchaChallenge> create(CaptchaChallenge captchaChallenge) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
            callLog.add("captcha.load");
            return Mono.just(Optional.ofNullable(challenge));
        }

        @Override
        public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId id, Instant verifiedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId id, Instant attemptedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId id, Instant lockedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId id, Instant consumedAt) {
            callLog.add("captcha.markConsumed");
            markConsumedCalls++;
            return Mono.just(challenge);
        }

        @Override
        public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId id, Instant refreshedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
            throw new UnsupportedOperationException();
        }
    }


    private static final class FakeLookupPort implements UsernameRecoveryLookupPort {

        private final List<String> callLog;
        private UsernameRecoveryLookupResult result;
        private boolean fails;
        private int searchCalls;

        private FakeLookupPort(List<String> callLog) {
            this.callLog = callLog;
        }

        @Override
        public Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand command) {
            callLog.add("lookup.search");
            searchCalls++;
            if (fails) {
                return Mono.error(new IllegalStateException("lookup infrastructure failure"));
            }
            return Mono.just(result);
        }
    }

    private static final class FakeSessionPort implements UsernameRecoverySessionPort {

        private final List<String> callLog;
        private UsernameRecoverySession stored;
        private boolean createFails;
        private UsernameRecoverySessionMutationOutcome createOutcome;
        private UsernameRecoverySessionMutationCommand lastMutation;
        private UsernameRecoverySessionMutationOutcome updateOutcome;
        private int createCalls;
        private int updateCalls;

        private FakeSessionPort(List<String> callLog) {
            this.callLog = callLog;
        }

        void store(UsernameRecoverySession session) {
            this.stored = session;
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> create(UsernameRecoverySession session) {
            callLog.add("session.create");
            createCalls++;
            if (createFails) {
                return Mono.error(new IllegalStateException("session create infrastructure failure"));
            }
            if (createOutcome == UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(
                        UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE));
            }
            stored = session;
            return Mono.just(new UsernameRecoverySessionMutationResult(
                    UsernameRecoverySessionMutationOutcome.CREATED, session));
        }

        @Override
        public Mono<UsernameRecoverySessionLoadResult> load(UsernameRecoverySessionId sessionId) {
            callLog.add("session.load");
            if (stored == null) {
                return Mono.just(UsernameRecoverySessionLoadResult.notFound());
            }
            return Mono.just(UsernameRecoverySessionLoadResult.loaded(stored));
        }

        @Override
        public Mono<UsernameRecoverySessionMutationResult> update(UsernameRecoverySessionMutationCommand command) {
            callLog.add("session.update");
            updateCalls++;
            lastMutation = command;
            if (updateOutcome != null) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(updateOutcome));
            }
            if (stored == null) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(
                        UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND));
            }
            if (!stored.revision().equals(command.expectedRevision())) {
                return Mono.just(UsernameRecoverySessionMutationResult.outcome(
                        UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT));
            }
            stored = command.updatedSession();
            return Mono.just(new UsernameRecoverySessionMutationResult(
                    UsernameRecoverySessionMutationOutcome.APPLIED, stored));
        }

        @Override
        public Mono<UsernameRecoverySessionDeleteResult> delete(UsernameRecoverySessionId sessionId) {
            throw new UnsupportedOperationException("start does not delete sessions");
        }
    }

}

