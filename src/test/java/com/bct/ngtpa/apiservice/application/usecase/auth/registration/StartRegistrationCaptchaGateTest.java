package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StartRegistrationCaptchaGateTest {

  private static final Instant NOW = Instant.parse("2026-07-07T00:00:00Z");
  private static final String HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final CaptchaChallengeId CHALLENGE_ID =
      new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final String ANSWER_HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort();
  private final RecordingHashGenerator hashGenerator = new RecordingHashGenerator();
  private final FakeLookupPort lookup = new FakeLookupPort();
  private final FakeSessionGateway session = new FakeSessionGateway();
  private final FakeCsrfGateway csrf = new FakeCsrfGateway();
  private final FakeOtpGateway otp = new FakeOtpGateway();
  private final FakeMessageGateway message = new FakeMessageGateway();
  private final AuthFlowSessionPort authFlowSessionPort = new NoOpAuthFlowSessionPort();
  private final OtpChallengeStatePort otpChallengeStatePort = new NoOpOtpChallengeStatePort();
  private final FakeCaptchaChallengePort captchaPort = new FakeCaptchaChallengePort();
  private final FakeAuthWorkflowPolicyPort policyPort = new FakeAuthWorkflowPolicyPort();
  private final RegistrationCaptchaGateService gateService =
      new RegistrationCaptchaGateService(captchaPort, Clock.fixed(NOW, ZoneOffset.UTC));

  private StartRegistrationService useCase;

  @BeforeEach
  void setUp() {
    idempotency.reset();
    lookup.reset();
    session.reset();
    otp.reset();
    message.reset();
    captchaPort.reset();
    useCase =
        new StartRegistrationService(
            new RegistrationStartValidator(),
            hashGenerator,
            idempotency,
            lookup,
            session,
            csrf,
            otp,
            message,
            authFlowSessionPort,
            otpChallengeStatePort,
            policyPort,
            gateService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void registrationStartDoesNotRequireCaptchaWhenPreStartChallengeDisabled() {
    policyPort.policy = policy(false);

    StepVerifier.create(useCase.start(command(null)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
    assertThat(lookup.called).isTrue();
  }

  @Test
  void registrationStartRequiresCaptchaWhenPreStartChallengeEnabled() {
    policyPort.policy = policy(true);

    StepVerifier.create(useCase.start(command("   ")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED);
              assertThat(result.errors())
                  .singleElement()
                  .satisfies(
                      error -> {
                        assertThat(error.errorKey())
                            .isEqualTo(RegistrationErrorKey.REGISTRATION_CAPTCHA_REQUIRED);
                        assertThat(error.target()).isEqualTo("captchaChallengeId");
                      });
            })
        .verifyComplete();

    assertThat(lookup.called).isFalse();
    assertThat(session.createdAfterLookup).isFalse();
    assertThat(otp.created).isFalse();
    assertThat(message.delivered).isFalse();
    assertThat(idempotency.failed).isTrue();
  }

  @Test
  void registrationStartConsumesVerifiedCaptchaBeforeApimLookup() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    assertThat(lookup.called).isTrue();
    assertThat(captchaPort.callOrder)
        .containsSubsequence("captcha.load", "captcha.consume", "session", "otp", "message");
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaNotVerified() {
    assertCaptchaFailure(
        CaptchaStatus.CREATED,
        NOW.plusSeconds(300),
        RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaExpired() {
    assertCaptchaFailure(
        CaptchaStatus.VERIFIED,
        NOW.minusSeconds(1),
        RegistrationErrorKey.REGISTRATION_CAPTCHA_EXPIRED);
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaLocked() {
    assertCaptchaFailure(
        CaptchaStatus.LOCKED,
        NOW.plusSeconds(300),
        RegistrationErrorKey.REGISTRATION_CAPTCHA_LOCKED);
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaConsumed() {
    assertCaptchaFailure(
        CaptchaStatus.CONSUMED,
        NOW.plusSeconds(300),
        RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED);
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaRefreshed() {
    assertCaptchaFailure(
        CaptchaStatus.REFRESHED,
        NOW.plusSeconds(300),
        RegistrationErrorKey.REGISTRATION_CAPTCHA_REFRESHFAILED);
  }

  @Test
  void registrationStartDoesNotCallApimWhenCaptchaNotFound() {
    policyPort.policy = policy(true);
    captchaPort.challenge = null;

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.errors())
                    .singleElement()
                    .satisfies(
                        error ->
                            assertThat(error.errorKey())
                                .isEqualTo(RegistrationErrorKey.REGISTRATION_CAPTCHA_NOTFOUND)))
        .verifyComplete();

    assertNoDownstreamSideEffects();
  }

  @Test
  void registrationStartConsumesCaptchaOnlyOnce() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    idempotency.outcome = RegistrationIdempotencyStartOutcome.STARTED;
    lookup.called = false;
    session.createdAfterLookup = false;
    otp.created = false;
    message.delivered = false;

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.errors())
                    .singleElement()
                    .satisfies(
                        error ->
                            assertThat(error.errorKey())
                                .isEqualTo(RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED)))
        .verifyComplete();

    assertNoDownstreamSideEffects();
  }

  @Test
  void idempotencyDuplicateDoesNotConsumeCaptchaAgain() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));
    idempotency.outcome = RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(result -> assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
  }

  @Test
  void idempotencyCompletedReplayDoesNotConsumeCaptchaAgain() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));
    idempotency.outcome = RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY;
    idempotency.record = idempotency.completedRecord();

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(StartRegistrationResultStatus.COMPLETED_REPLAY))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
  }

  @Test
  void idempotencyKeyReuseDoesNotConsumeCaptcha() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));
    idempotency.outcome = RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.errors())
                    .singleElement()
                    .satisfies(
                        error ->
                            assertThat(error.errorKey())
                                .isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED)))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
  }

  @Test
  void localValidationFailureDoesNotConsumeCaptcha() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));
    var invalid =
        new StartRegistrationCommand(
            new RegistrationIdempotencyKey("idem_abc123"),
            "BAD",
            "x",
            "31/02/1980",
            null,
            null,
            CHALLENGE_ID.value(),
            null,
            "en",
            "corr",
            Duration.ofMinutes(5));

    StepVerifier.create(useCase.start(invalid))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
    assertThat(lookup.called).isFalse();
  }

  @Test
  void captchaGateFailureMarksIdempotencyAccordingToStrategy() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.CREATED, NOW.plusSeconds(300));

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED))
        .verifyComplete();

    assertThat(idempotency.failed).isTrue();
    assertThat(idempotency.failedReason)
        .isEqualTo(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID.value());
  }

  @Test
  void requestHashIncludesCaptchaChallengeIdWhenPresent() {
    policyPort.policy = policy(false);

    StepVerifier.create(useCase.start(command("  " + CHALLENGE_ID.value() + "  ")))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    assertThat(hashGenerator.action).isEqualTo(RegistrationIdempotencyAction.REGISTRATION_START);
    assertThat(hashGenerator.parts)
        .containsExactly(
            "GPFS",
            "1234567890",
            "31/01/1980",
            "email",
            "member@example.com",
            CHALLENGE_ID.value());
  }

  private void assertCaptchaFailure(CaptchaStatus status, Instant expiresAt, Object expectedKey) {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(status, expiresAt);

    StepVerifier.create(useCase.start(command(CHALLENGE_ID.value())))
        .assertNext(
            result ->
                assertThat(result.errors())
                    .singleElement()
                    .satisfies(
                        error -> {
                          assertThat(error.errorKey()).isEqualTo(expectedKey);
                          assertThat(error.target()).isEqualTo("captchaChallengeId");
                        }))
        .verifyComplete();

    assertNoDownstreamSideEffects();
    assertThat(idempotency.failed).isTrue();
  }

  private void assertNoDownstreamSideEffects() {
    assertThat(lookup.called).isFalse();
    assertThat(session.createdAfterLookup).isFalse();
    assertThat(otp.created).isFalse();
    assertThat(message.delivered).isFalse();
  }

  private static StartRegistrationCommand command(String captchaChallengeId) {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey("idem_abc123"),
        "GPFS",
        "1234567890",
        "31/01/1980",
        "member@example.com",
        null,
        captchaChallengeId,
        null,
        "en",
        "corr-secret",
        Duration.ofMinutes(5));
  }

  private static RegistrationAuthWorkflowPolicy policy(boolean enabled) {
    return new RegistrationAuthWorkflowPolicy(
        new PreStartChallengePolicy(enabled, enabled ? PreStartChallengeType.CAPTCHA : null),
        new CaptchaPolicy(
            7, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", true, 300, 5),
        null,
        null,
        null);
  }

  private static CaptchaChallenge captcha(CaptchaStatus status, Instant expiresAt) {
    return new CaptchaChallenge(
        CHALLENGE_ID,
        status == CaptchaStatus.CONSUMED || status == CaptchaStatus.REFRESHED
            ? null
            : new CaptchaAnswerHash(ANSWER_HASH),
        status,
        0,
        5,
        NOW.minusSeconds(30),
        expiresAt,
        status == CaptchaStatus.VERIFIED ? NOW.minusSeconds(5) : null,
        status == CaptchaStatus.CONSUMED ? NOW.minusSeconds(2) : null,
        status == CaptchaStatus.REFRESHED ? NOW.minusSeconds(2) : null,
        status == CaptchaStatus.LOCKED ? NOW.minusSeconds(2) : null);
  }

  private static final class RecordingHashGenerator implements RegistrationRequestHashGenerator {
    private RegistrationIdempotencyAction action;
    private List<String> parts = List.of();

    @Override
    public RegistrationRequestHash hash(
        RegistrationIdempotencyAction action, List<String> normalizedParts) {
      this.action = action;
      this.parts = List.copyOf(normalizedParts);
      return new RegistrationRequestHash(HASH);
    }
  }

  private static final class FakeAuthWorkflowPolicyPort implements AuthWorkflowPolicyPort {
    private RegistrationAuthWorkflowPolicy policy = policy(false);

    @Override
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
      return policy;
    }

    @Override
    public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
      return Optional.empty();
    }
  }

  private static final class FakeCaptchaChallengePort implements CaptchaChallengePort {
    private final List<String> callOrder = new ArrayList<>();
    private CaptchaChallenge challenge;
    private int loadCalls;

    @Override
    public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
      this.challenge = challenge;
      return Mono.just(challenge);
    }

    @Override
    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId captchaChallengeId) {
      loadCalls++;
      callOrder.add("captcha.load");
      return Mono.just(Optional.ofNullable(challenge));
    }

    @Override
    public Mono<CaptchaChallenge> markVerified(
        CaptchaChallengeId captchaChallengeId, Instant verifiedAt) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> incrementFailedAttempt(
        CaptchaChallengeId captchaChallengeId, Instant attemptedAt) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markLocked(
        CaptchaChallengeId captchaChallengeId, Instant lockedAt) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markConsumed(
        CaptchaChallengeId captchaChallengeId, Instant consumedAt) {
      callOrder.add("captcha.consume");
      challenge =
          new CaptchaChallenge(
              challenge.captchaChallengeId(),
              null,
              CaptchaStatus.CONSUMED,
              challenge.failedAttemptCount(),
              challenge.maxAttempts(),
              challenge.createdAt(),
              challenge.expiresAt(),
              challenge.verifiedAt(),
              consumedAt,
              challenge.refreshedAt(),
              challenge.lockedAt());
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markRefreshed(
        CaptchaChallengeId captchaChallengeId, Instant refreshedAt) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<Void> delete(CaptchaChallengeId captchaChallengeId) {
      challenge = null;
      return Mono.empty();
    }

    private void reset() {
      callOrder.clear();
      challenge = null;
      loadCalls = 0;
    }
  }

  private static final class NoOpAuthFlowSessionPort implements AuthFlowSessionPort {
    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.empty();
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
      return Mono.just(Optional.empty());
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
      return Mono.empty();
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.empty();
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.empty();
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.empty();
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
      return Mono.empty();
    }
  }

  private static final class NoOpOtpChallengeStatePort implements OtpChallengeStatePort {
    @Override
    public Mono<Void> save(OtpChallenge challenge) {
      return Mono.empty();
    }

    @Override
    public Mono<Optional<OtpChallenge>> findById(String otpChallengeId) {
      return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Void> update(OtpChallenge challenge) {
      return Mono.empty();
    }

    @Override
    public Mono<Void> delete(String otpChallengeId) {
      return Mono.empty();
    }
  }

  private static final class FakeIdempotencyPort implements RegistrationIdempotencyPort {
    private RegistrationIdempotencyStartOutcome outcome =
        RegistrationIdempotencyStartOutcome.STARTED;
    private RegistrationIdempotencyRecord record = processingRecord();
    private boolean failed;
    private String failedReason;

    @Override
    public Mono<RegistrationIdempotencyStartResult> startProcessing(
        RegistrationIdempotencyStartCommand command) {
      return Mono.just(new RegistrationIdempotencyStartResult(outcome, record));
    }

    @Override
    public Mono<RegistrationIdempotencyRecord> markCompleted(
        RegistrationIdempotencyCompleteCommand command) {
      return Mono.just(completedRecord());
    }

    @Override
    public Mono<RegistrationIdempotencyRecord> markFailed(
        RegistrationIdempotencyFailCommand command) {
      failed = true;
      failedReason = command.reasonCode();
      return Mono.just(processingRecord());
    }

    @Override
    public Mono<Optional<RegistrationIdempotencyRecord>> load(
        RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
      return Mono.just(Optional.empty());
    }

    @Override
    public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
      return Mono.empty();
    }

    private static RegistrationIdempotencyRecord processingRecord() {
      return new RegistrationIdempotencyRecord(
          RegistrationIdempotencyAction.REGISTRATION_START,
          new RegistrationIdempotencyKey("idem_abc123"),
          new RegistrationRequestHash(HASH),
          RegistrationIdempotencyStatus.PROCESSING,
          null,
          NOW,
          NOW,
          NOW.plusSeconds(300));
    }

    private RegistrationIdempotencyRecord completedRecord() {
      return new RegistrationIdempotencyRecord(
          RegistrationIdempotencyAction.REGISTRATION_START,
          new RegistrationIdempotencyKey("idem_abc123"),
          new RegistrationRequestHash(HASH),
          RegistrationIdempotencyStatus.COMPLETED,
          new RegistrationIdempotencyResponseSnapshot(
              "200", "{\"nextStep\":\"VERIFY_OTP\"}", Map.of()),
          NOW,
          NOW,
          NOW.plusSeconds(300));
    }

    private void reset() {
      outcome = RegistrationIdempotencyStartOutcome.STARTED;
      record = processingRecord();
      failed = false;
      failedReason = null;
    }
  }

  private static final class FakeLookupPort implements RegistrationLookupPort {
    private final List<String> callOrder = new ArrayList<>();
    private boolean called;

    @Override
    public Mono<RegistrationLookupResult> searchUnregisteredMember(
        RegistrationLookupCommand command) {
      called = true;
      callOrder.add("lookup");
      return Mono.just(
          new RegistrationLookupResult(
              RegistrationLookupStatus.ELIGIBLE,
              List.of(new RegistrationLookupAccount("apim@example.com", "49998888", "OI", "JPM")),
              null));
    }

    private void reset() {
      callOrder.clear();
      called = false;
    }
  }

  private final class FakeSessionGateway implements RegistrationSessionPort {
    private boolean createdAfterLookup;

    @Override
    public Mono<String> createRegistrationSession(
        String maskedDestination, RegistrationContactType contactType) {
      createdAfterLookup = true;
      captchaPort.callOrder.add("session");
      return Mono.just("afs_session_for_cookie");
    }

    @Override
    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String maskedDestination) {
      return Mono.empty();
    }

    private void reset() {
      createdAfterLookup = false;
    }
  }

  private static final class FakeCsrfGateway implements RegistrationCsrPort {
    @Override
    public Mono<String> bindCsrfToken(String sid) {
      return Mono.just("csrf_token_safe");
    }
  }

  private final class FakeOtpGateway implements RegistrationOtpPort {
    private boolean created;

    @Override
    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      created = true;
      captchaPort.callOrder.add("otp");
      return Mono.just(
          new RegistrationOtpCreationResult("otp_challenge_internal", "123456", 300, 60, 3));
    }

    private void reset() {
      created = false;
    }
  }

  private final class FakeMessageGateway implements RegistrationMessagePort {
    private boolean delivered;

    @Override
    public Mono<Boolean> deliverRegistrationOtp(
        RegistrationContactType type,
        String recipient,
        String otpCode,
        int expiresInSeconds,
        String acceptLanguage,
        String correlationId) {
      delivered = true;
      captchaPort.callOrder.add("message");
      return Mono.just(true);
    }

    private void reset() {
      delivered = false;
    }
  }
}
