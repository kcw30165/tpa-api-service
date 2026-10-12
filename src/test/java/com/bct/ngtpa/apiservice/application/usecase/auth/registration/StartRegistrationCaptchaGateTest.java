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
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

  private final FakeLookupPort lookup = new FakeLookupPort();
  private final FakeSessionGateway session = new FakeSessionGateway();
  private final FakeCsrfGateway csrf = new FakeCsrfGateway();
  private final FakeOtpGateway otp = new FakeOtpGateway();
  private final FakeMessageGateway message = new FakeMessageGateway();
  private final FakeCaptchaChallengePort captchaPort = new FakeCaptchaChallengePort();
  private final FakeAuthWorkflowPolicyPort policyPort = new FakeAuthWorkflowPolicyPort();
  private final RegistrationCaptchaGateService gateService =
      new RegistrationCaptchaGateService(captchaPort, Clock.fixed(NOW, ZoneOffset.UTC));

  private RegistrationStartProtectedOperation useCase;

  @BeforeEach
  void setUp() {
    lookup.reset();
    session.reset();
    otp.reset();
    message.reset();
    captchaPort.reset();
    useCase =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            session,
            csrf,
            otp,
            message,
            policyPort,
            gateService,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void registrationStartDoesNotRequireCaptchaWhenPreStartChallengeDisabled() {
    policyPort.policy = policy(false);

    StepVerifier.create(useCase.execute(command(null)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
    assertThat(lookup.called).isTrue();
  }

  @Test
  void registrationStartRequiresCaptchaWhenPreStartChallengeEnabled() {
    policyPort.policy = policy(true);

    StepVerifier.create(useCase.execute(command("   ")))
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
  }

  @Test
  void registrationStartConsumesVerifiedCaptchaBeforeApimLookup() {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(CaptchaStatus.VERIFIED, NOW.plusSeconds(300));

    StepVerifier.create(useCase.execute(command(CHALLENGE_ID.value())))
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

    StepVerifier.create(useCase.execute(command(CHALLENGE_ID.value())))
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

    StepVerifier.create(useCase.execute(command(CHALLENGE_ID.value())))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .verifyComplete();

    lookup.called = false;
    session.createdAfterLookup = false;
    otp.created = false;
    message.delivered = false;

    StepVerifier.create(useCase.execute(command(CHALLENGE_ID.value())))
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

    StepVerifier.create(useCase.execute(invalid))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED))
        .verifyComplete();

    assertThat(captchaPort.loadCalls).isZero();
    assertThat(lookup.called).isFalse();
  }

  private void assertCaptchaFailure(CaptchaStatus status, Instant expiresAt, Object expectedKey) {
    policyPort.policy = policy(true);
    captchaPort.challenge = captcha(status, expiresAt);

    StepVerifier.create(useCase.execute(command(CHALLENGE_ID.value())))
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

  private static final class FakeLookupPort implements RegistrationLookupPort {
    private final List<String> callOrder = new ArrayList<>();
    private boolean called;

    @Override
    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      called = true;
      callOrder.add("lookup");
      return Mono.just(
          new RegistrationLookupResult(
              RegistrationLookupStatus.ELIGIBLE,
              List.of(new RegistrationLookupAccount("OI", "JPM", null, null, null, null, null, Boolean.FALSE, null)),
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
