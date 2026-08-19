package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
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
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
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
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.SecureRandomCsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
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
 * creation with opaque Scheme-Refs and hashed CSRF, safe replay completion, and sanitized failure
 * mapping.
 *
 * <p>Design references: {@code .cline/tasks/019-start-use-case.md} required orchestration order and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8
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
  private static final String CONTEXT_SECRET = "0123456789abcdef0123456789abcdef";

  private final List<String> callLog = new ArrayList<>();
  private final FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort(callLog);
  private final FakeCaptchaChallengePort captchaPort =
      new FakeCaptchaChallengePort(
          callLog, challenge(CaptchaStatus.VERIFIED, NOW.plusSeconds(300)));
  private final FakeLookupPort lookupPort = new FakeLookupPort(callLog);
  private final FakeAuthFlowSessionPort authFlowSessionPort = new FakeAuthFlowSessionPort(callLog);
  private final UsernameRecoveryAuthFlowContextBinding contextBinding =
      new UsernameRecoveryAuthFlowContextBinding(
          new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", CONTEXT_SECRET));

  private final RegistrationCaptchaGateService captchaGateService =
      new RegistrationCaptchaGateService(captchaPort, CLOCK);
  private final CsrfTokenGenerator csrfTokenGenerator = new SecureRandomCsrfTokenGenerator();
  private final CsrfTokenHasher csrfTokenHasher = new Sha256CsrfTokenHasher();

  private StartUsernameRecoveryService service;

  @BeforeEach
  void setUp() {
    lookupPort.result = matchedLookup();
    service =
        new StartUsernameRecoveryService(
            new UsernameRecoveryStartValidator(),
            new UsernameRecoveryStartFingerprintGenerator(),
            idempotencyPort,
            captchaGateService,
            lookupPort,
            authFlowSessionPort,
            contextBinding,
            csrfTokenGenerator,
            csrfTokenHasher,
            CLOCK,
            TTL_SECONDS);
  }

  @Test
  void invalidInputMapsToTypedValidationFailureWithoutSideEffects() {
    StepVerifier.create(
            service.start(
                new UsernameRecoveryStartCommand(
                    MemberIdentityType.STAFFNO,
                    "3030609",
                    "04/07/1978",
                    null,
                    null,
                    CAPTCHA,
                    LANGUAGE,
                    IDEM_KEY)))
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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

    StepVerifier.create(service.start(command)).expectNextCount(1).verifyComplete();

    assertThat(callLog).startsWith("idem.reserve");
    UsernameRecoveryIdempotencyPort.ReserveCommand reserve = idempotencyPort.lastReserveCommand;
    assertThat(reserve.action()).isEqualTo(UsernameRecoveryIdempotencyAction.START);
    assertThat(reserve.idempotencyKey().value()).isEqualTo(IDEM_KEY);
    assertThat(reserve.fingerprint())
        .isEqualTo(new UsernameRecoveryStartFingerprintGenerator().derive(command));
    assertThat(reserve.operationReference().value()).isNotBlank();
    assertThat(reserve.now()).isEqualTo(NOW);
    assertThat(reserve.ttl().toSeconds()).isEqualTo(TTL_SECONDS);
  }

  @Test
  void duplicateInProgressMapsToTypedInProgressSignalWithoutSideEffects() {
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.IDEMPOTENCY_IN_PROGRESS);
            })
        .verify();

    assertThat(callLog).containsExactly("idem.reserve");
  }

  @Test
  void keyReusedMapsToTypedKeyReusedSignal() {
    idempotencyPort.reserveOutcome =
        UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(callLog).containsExactly("idem.reserve");
  }

  @Test
  void completedSessionReplayLoadsModelBAndRotatesEnvelopeCsrfWithoutExtendingExpiry() {
    AuthFlowSessionId id = new AuthFlowSessionId("afs_opaque-1");
    authFlowSessionPort.store(activeEnvelopeWithContext(id));
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord = completedRecord(UsernameRecoveryStartReplayCodec.encode(id));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(Outcome.SESSION_CREATED);
              assertThat(result.authFlowSessionId()).isEqualTo(id);
              assertThat(result.csrfToken()).isNotBlank();
              assertThat(result.expiresInSeconds()).isEqualTo(TTL_SECONDS);
              assertThat(result.schemes()).hasSize(2);
              assertThat(authFlowSessionPort.lastMutation.updated().expiresAt())
                  .isEqualTo(NOW.plusSeconds(TTL_SECONDS));
              assertThat(authFlowSessionPort.lastMutation.updated().csrfTokenHash())
                  .isEqualTo(csrfTokenHasher.hash(result.csrfToken()));
            })
        .verifyComplete();

    assertThat(callLog)
        .containsExactly(
            "idem.reserve", "idem.resolve", "authFlow.loadSnapshot", "authFlow.updateRevisioned");
    assertThat(idempotencyPort.completeCalls).isZero();
  }

  @Test
  void completedSessionReplayRevisionConflictIsDistinctAndNotRetried() {
    AuthFlowSessionId id = new AuthFlowSessionId("afs_opaque-1");
    authFlowSessionPort.store(activeEnvelope(id));
    authFlowSessionPort.updateOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
    authFlowSessionPort.store(activeEnvelopeWithContext(id));
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord = completedRecord(UsernameRecoveryStartReplayCodec.encode(id));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error ->
                assertThat(((UsernameRecoveryStartException) error).reason())
                    .isEqualTo(Reason.REPLAY_REVISION_CONFLICT))
        .verify();

    assertThat(authFlowSessionPort.updateCalls).isEqualTo(1);
  }

  @Test
  void completedNonSessionReplayReturnsOriginalResultWithoutReExecuting() {
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    UsernameRecoveryStartResult original =
        new UsernameRecoveryStartResult(Outcome.MEMBER_NOT_FOUND, null, null, List.of());
    idempotencyPort.storedRecord =
        completedRecord(UsernameRecoveryStartReplaySnapshot.encode(original));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(result -> assertThat(result).isEqualTo(original))
        .verifyComplete();

    assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
    assertThat(captchaPort.markConsumedCalls).isZero();
    assertThat(lookupPort.searchCalls).isZero();
  }

  @Test
  void corruptReplaySnapshotFailsClosedAsSystemFailure() {
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord =
        completedRecord(new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("corrupt-payload"));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
  }

  @Test
  void replayWithMissingRecordFailsClosedAsSystemFailure() {
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord = null;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(callLog).containsExactly("idem.reserve", "idem.resolve");
  }

  @Test
  void replayWithExpiredEnvelopeFailsClosedWithoutRotation() {
    AuthFlowSessionId id = new AuthFlowSessionId("afs_expired");
    AuthFlowSession protectedActive = activeEnvelopeWithContext(id);
    authFlowSessionPort.store(
        new AuthFlowSession(
            id,
            protectedActive.scenario(),
            protectedActive.status(),
            protectedActive.csrfTokenHash(),
            protectedActive.activeOtpChallengeId(),
            protectedActive.data(),
            protectedActive.createdAt(),
            protectedActive.updatedAt(),
            NOW.minusSeconds(1)));
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord = completedRecord(UsernameRecoveryStartReplayCodec.encode(id));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error ->
                assertThat(((UsernameRecoveryStartException) error).reason())
                    .isEqualTo(Reason.SYSTEM_FAILURE))
        .verify();

    assertThat(authFlowSessionPort.updateCalls).isZero();
    assertThat(callLog).containsExactly("idem.reserve", "idem.resolve", "authFlow.loadSnapshot");
  }

  @Test
  void replayWithAbsentEnvelopeFailsClosedWithoutBlindCreation() {
    AuthFlowSessionId id = new AuthFlowSessionId("afs_gone");
    idempotencyPort.reserveOutcome = UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
    idempotencyPort.storedRecord = completedRecord(UsernameRecoveryStartReplayCodec.encode(id));
    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error ->
                assertThat(((UsernameRecoveryStartException) error).reason())
                    .isEqualTo(Reason.SYSTEM_FAILURE))
        .verify();
    assertThat(authFlowSessionPort.createCalls).isZero();
  }

  @Test
  void captchaNotVerifiedRejectsBeforeLookup() {
    captchaPort.challenge = challenge(CaptchaStatus.CREATED, NOW.plusSeconds(300));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.CAPTCHA_INVALID);
            })
        .verify();

    assertThat(callLog).containsExactly("idem.reserve");
  }

  @Test
  void lookupRunsOnlyAfterReservationAndVerifiedCaptchaConsumption() {
    StepVerifier.create(service.start(emailCommand(EMAIL))).expectNextCount(1).verifyComplete();

    assertThat(callLog)
        .startsWith("idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
    assertThat(captchaPort.markConsumedCalls).isEqualTo(1);
    assertThat(lookupPort.searchCalls).isEqualTo(1);
  }

  @Test
  void sessionCreatedResultUsesTheSingleProtectedEnvelopeIdentity() {
    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(Outcome.SESSION_CREATED);
              assertThat(result.authFlowSessionId())
                  .isEqualTo(authFlowSessionPort.lastCreate.authFlowSessionId());
              assertThat(result.csrfToken()).isNotBlank();
              var stored = authFlowSessionPort.createdSnapshot.session();
              assertThat(contextBinding.load(stored).candidates()).hasSize(2);
              assertThat(stored.data().attributes())
                  .containsKeys(
                      "usernameRecoveryContext.ciphertext",
                      "usernameRecoveryContext.keyVersion",
                      "usernameRecoveryContext.algorithm");
            })
        .verifyComplete();
    assertThat(authFlowSessionPort.createCalls).isEqualTo(1);
    assertThat(callLog).doesNotContain("workflowState.create");
  }

  @Test
  void nonSessionOutcomesCarryNoSessionIdOrRawCsrf() {
    lookupPort.result =
        new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
              assertThat(result.authFlowSessionId()).isNull();
              assertThat(result.csrfToken()).isNull();
              assertThat(result.expiresInSeconds()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void memberNotFoundReturnsTypedResultAndCompletesIdempotency() {
    lookupPort.result =
        new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(Outcome.MEMBER_NOT_FOUND);
              assertThat(result.schemes()).isEmpty();
              assertThat(result.csrfToken()).isNull();
              assertThat(result.expiresInSeconds()).isNull();
            })
        .verifyComplete();

    assertThat(callLog)
        .containsExactly(
            "idem.reserve",
            "captcha.load",
            "captcha.markConsumed",
            "lookup.search",
            "idem.complete");
    UsernameRecoveryStartReplaySnapshot.DecodeResult decoded =
        UsernameRecoveryStartReplaySnapshot.decode(idempotencyPort.lastCompleted.replaySnapshot());
    assertThat(decoded.decoded()).isTrue();
    assertThat(decoded.result().outcome()).isEqualTo(Outcome.MEMBER_NOT_FOUND);
  }

  @Test
  void zeroRegisteredCreatesNoSessionAndReturnsRegistrationGuidance() {
    lookupPort.result =
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.MATCHED,
            List.of(new UsernameRecoveryLookupCandidate("DB", "DBA", false, null)));

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(Outcome.ZERO_REGISTERED);
              assertThat(result.schemes()).hasSize(1);
              assertThat(result.schemes().get(0).registered()).isFalse();
              assertThat(result.schemes().get(0).schemeRef()).isNull();
              assertThat(result.csrfToken()).isNull();
              assertThat(result.expiresInSeconds()).isNull();
            })
        .verifyComplete();

    assertThat(callLog)
        .containsExactly(
            "idem.reserve",
            "captcha.load",
            "captcha.markConsumed",
            "lookup.search",
            "idem.complete");
    UsernameRecoveryStartReplaySnapshot.DecodeResult decoded =
        UsernameRecoveryStartReplaySnapshot.decode(idempotencyPort.lastCompleted.replaySnapshot());
    assertThat(decoded.decoded()).isTrue();
    assertThat(decoded.result().outcome()).isEqualTo(Outcome.ZERO_REGISTERED);
    assertThat(decoded.result().schemes().get(0).trustCode()).isEqualTo("DBA");
  }

  @Test
  void lookupContractInvalidMapsToSystemFailureWithoutCompletion() {
    lookupPort.result =
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.CONTRACT_INVALID, List.of());

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(callLog)
        .containsExactly("idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
    assertThat(idempotencyPort.completeCalls).isZero();
  }

  @Test
  void lookupInfrastructureExceptionMapsToSystemFailure() {
    lookupPort.fails = true;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(callLog)
        .containsExactly("idem.reserve", "captcha.load", "captcha.markConsumed", "lookup.search");
    assertThat(idempotencyPort.completeCalls).isZero();
  }

  @Test
  void registeredCandidatesCreateOneProtectedEnvelopeWithoutSidecar() {
    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(Outcome.SESSION_CREATED);
              assertThat(result.expiresInSeconds()).isEqualTo(TTL_SECONDS);
              assertThat(result.schemes()).hasSize(2);
            })
        .verifyComplete();
    assertThat(callLog)
        .containsExactly(
            "idem.reserve",
            "captcha.load",
            "captcha.markConsumed",
            "lookup.search",
            "authFlow.createRevisioned",
            "idem.complete");
    var created = authFlowSessionPort.createdSnapshot.session();
    assertThat(created.scenario().name()).isEqualTo("USERNAME_RECOVERY");
    assertThat(created.status().name()).isEqualTo("STARTED");
    assertThat(created.createdAt()).isEqualTo(NOW);
    assertThat(created.expiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
    assertThat(created.data().contactType()).isEqualTo("EMAIL");
    assertThat(created.data().attributes()).hasSize(3);
    var context = contextBinding.load(created);
    assertThat(context.workflowStatus()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
    assertThat(context.verifiedDestination())
        .isEqualTo(
            new UsernameRecoveryVerifiedDestination(
                EMAIL, UsernameRecoveryVerifiedDestination.Channel.EMAIL));
    assertThat(context.candidates()).hasSize(2);
  }

  @Test
  void sessionCreateInfrastructureErrorMapsToSystemFailure() {
    authFlowSessionPort.createFails = true;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
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
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();

    assertThat(authFlowSessionPort.createCalls).isEqualTo(1);
    assertThat(authFlowSessionPort.createCalls).isEqualTo(1);
    assertThat(callLog).contains("authFlow.createRevisioned");
  }

  @Test
  void completeFailureAfterMemberNotFoundMapsToSystemFailure() {
    lookupPort.result =
        new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());
    idempotencyPort.completeFails = true;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
              assertThat(((UsernameRecoveryStartException) error).reason())
                  .isEqualTo(Reason.SYSTEM_FAILURE);
            })
        .verify();
  }

  @Test
  void captchaRemainsConsumedWhenLookupFails() {
    lookupPort.fails = true;

    StepVerifier.create(service.start(emailCommand(EMAIL)))
        .expectErrorSatisfies(
            error -> {
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
        .assertNext(
            result -> {
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
        .expectErrorSatisfies(
            error ->
                assertThat(error).isInstanceOf(AssertionError.class).hasMessage("reactive defect"))
        .verify();
  }

  @Test
  void unexpectedProgrammingDefectPropagatesToCentralBoundary() {
    UsernameRecoveryIdempotencyPort defective =
        new UsernameRecoveryIdempotencyPort() {
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
          public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(
              UnknownOutcomeCommand command) {
            return Mono.error(new AssertionError("defect"));
          }
        };
    StartUsernameRecoveryService defectiveService =
        new StartUsernameRecoveryService(
            new UsernameRecoveryStartValidator(),
            new UsernameRecoveryStartFingerprintGenerator(),
            defective,
            captchaGateService,
            lookupPort,
            authFlowSessionPort,
            contextBinding,
            csrfTokenGenerator,
            csrfTokenHasher,
            CLOCK,
            TTL_SECONDS);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> defectiveService.start(emailCommand(EMAIL)))
        .isInstanceOf(AssertionError.class)
        .hasMessage("defect");
  }

  private static UsernameRecoveryStartCommand emailCommand(String email) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        email,
        null,
        CAPTCHA,
        LANGUAGE,
        IDEM_KEY);
  }

  private static UsernameRecoveryStartCommand mobileCommand(String mobile) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        null,
        mobile,
        CAPTCHA,
        LANGUAGE,
        IDEM_KEY);
  }

  private static UsernameRecoveryStartCommand command(
      String email, String mobile, String captchaChallengeId, String idempotencyKey) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        email,
        mobile,
        captchaChallengeId,
        LANGUAGE,
        idempotencyKey);
  }

  private static UsernameRecoveryLookupResult matchedLookup() {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.MATCHED,
        List.of(
            new UsernameRecoveryLookupCandidate("DB", "DBA", true, "user-1"),
            new UsernameRecoveryLookupCandidate("DB", "DBB", false, null)));
  }

  private static CaptchaChallenge challenge(CaptchaStatus status, Instant expiresAt) {
    return new CaptchaChallenge(
        new CaptchaChallengeId(CAPTCHA),
        new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
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

  private static AuthFlowSession activeEnvelope(AuthFlowSessionId id) {
    return new AuthFlowSession(
        id,
        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario
            .USERNAME_RECOVERY,
        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.STARTED,
        new Sha256CsrfTokenHasher().hash("original-token"),
        null,
        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData.empty(),
        NOW,
        NOW,
        NOW.plusSeconds(TTL_SECONDS));
  }

  private AuthFlowSession activeEnvelopeWithContext(AuthFlowSessionId id) {
    AuthFlowSession base = activeEnvelope(id);
    UsernameRecoverySchemeRef registeredRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    UsernameRecoverySchemeRef unregisteredRef = new UsernameRecoverySchemeRef("sch_opaque-2");
    List<UsernameRecoverySessionCandidate> candidates =
        List.of(
            new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                    registeredRef,
                    "DB",
                    "DBA",
                    true,
                    "user-1",
                    UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null),
            new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                    unregisteredRef,
                    "DB",
                    "DBB",
                    false,
                    null,
                    UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null));
    UsernameRecoveryAuthFlowContext context =
        new UsernameRecoveryAuthFlowContext(
            UsernameRecoveryWorkflowStatus.ACTIVE,
            new UsernameRecoveryVerifiedDestination(
                EMAIL, UsernameRecoveryVerifiedDestination.Channel.EMAIL),
            LANGUAGE,
            candidates);
    return contextBinding.bind(base, context, NOW);
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
    private UsernameRecoveryIdempotencyOutcome reserveOutcome =
        UsernameRecoveryIdempotencyOutcome.RESERVED;
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
        UsernameRecoveryIdempotencyReservation reservation =
            new UsernameRecoveryIdempotencyReservation(
                command.action(),
                command.idempotencyKey(),
                command.fingerprint(),
                command.operationReference(),
                command.expiresAt());
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
      return Mono.just(
          new UsernameRecoveryIdempotencyRecord(
              command.action(),
              command.idempotencyKey(),
              command.fingerprint(),
              command.operationReference(),
              UsernameRecoveryIdempotencyRecord.Status.COMPLETED,
              command.replaySnapshot(),
              command.now(),
              command.now(),
              command.now().plus(command.ttl())));
    }

    @Override
    public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(
        UnknownOutcomeCommand command) {
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
    public Mono<CaptchaChallenge> incrementFailedAttempt(
        CaptchaChallengeId id, Instant attemptedAt) {
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

  private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
    private final List<String> callLog;
    private AuthFlowSessionCreateCommand lastCreate;
    private AuthFlowSessionSnapshot createdSnapshot;
    private boolean createFails;
    private AuthFlowSessionSnapshot storedSnapshot;
    private RevisionedAuthFlowSessionMutationCommand lastMutation;
    private AuthFlowSessionMutationOutcome updateOutcome;
    private int createCalls;
    private int updateCalls;

    private FakeAuthFlowSessionPort(List<String> callLog) {
      this.callLog = callLog;
    }

    private void store(AuthFlowSession session) {
      storedSnapshot =
          new AuthFlowSessionSnapshot(
              session, new SessionRevision("0123456789abcdef0123456789abcdef"));
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSessionSnapshot> createRevisioned(AuthFlowSessionCreateCommand command) {
      callLog.add("authFlow.createRevisioned");
      createCalls++;
      lastCreate = command;
      if (createFails) return Mono.error(new IllegalStateException("envelope create failed"));
      AuthFlowSession session =
          new AuthFlowSession(
              command.authFlowSessionId(),
              command.scenario(),
              command.initialStatus(),
              command.csrfTokenHash(),
              command.activeOtpChallengeId(),
              command.data(),
              command.createdAt(),
              command.createdAt(),
              command.expiresAt());
      createdSnapshot =
          new AuthFlowSessionSnapshot(
              session, new SessionRevision("0123456789abcdef0123456789abcdef"));
      return Mono.just(createdSnapshot);
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      return Mono.just(
          storedSnapshot == null ? Optional.empty() : Optional.of(storedSnapshot.session()));
    }

    @Override
    public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
      callLog.add("authFlow.loadSnapshot");
      return Mono.just(Optional.ofNullable(storedSnapshot));
    }

    @Override
    public Mono<
            com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult>
        updateRevisioned(RevisionedAuthFlowSessionMutationCommand command) {
      callLog.add("authFlow.updateRevisioned");
      updateCalls++;
      lastMutation = command;
      if (updateOutcome != null) {
        return Mono.just(
            com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult
                .outcome(updateOutcome));
      }
      storedSnapshot =
          new AuthFlowSessionSnapshot(
              command.updated(), new SessionRevision("11111111111111111111111111111111"));
      return Mono.just(
          new com.bct.ngtpa.apiservice.application.dto.session.authflow
              .AuthFlowSessionMutationResult(
              AuthFlowSessionMutationOutcome.APPLIED, storedSnapshot));
    }
  }
}
