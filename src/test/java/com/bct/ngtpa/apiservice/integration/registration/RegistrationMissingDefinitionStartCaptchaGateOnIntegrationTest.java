package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDeliveryAdapter;
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
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-stage II-2.e (decision D7), the production path of START. {@code application.yml} switches
 * the pre-start captcha challenge on; only the local profile switches it off. With the gate on,
 * {@link RegistrationStartProtectedOperation} ends its chain with an {@code onErrorResume} that
 * turns any error into a {@code SYSTEM_FAILED} result with the error key {@code SYSTEM_UNAVAILABLE}
 * on the target {@code PAGE}. A missing {@code registration-otp} definition throws while the chain
 * is being assembled, after the captcha has been consumed, so START returns that result instead of
 * an error signal, and nothing is sent.
 *
 * <p>The sibling class {@code RegistrationMissingDefinitionStartResendIntegrationTest} covers START
 * with the gate off and RESEND, where the original error reaches the caller. The state of the
 * idempotency key and the number of session updates are deliberately not asserted.
 */
class RegistrationMissingDefinitionStartCaptchaGateOnIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  private static final Duration WAIT = Duration.ofSeconds(10);
  private static final CaptchaChallengeId CHALLENGE_ID =
      new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final String ANSWER_HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String MISSING_MESSAGE =
      "No notice-templates.definitions entry for registration-otp";

  private final List<String> events = new ArrayList<>();

  @Test
  void aMissingDefinitionBecomesASystemFailedResultAfterTheCaptchaIsSpentAndNothingIsSent() {
    Registry registry = Registry.missing(events);
    CaptchaPort captcha = new CaptchaPort(events);
    MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    RegistrationStartProtectedOperation operation =
        operation(captcha, new RegistrationMessageDeliveryAdapter(delivery, registry));

    StepVerifier.create(operation.execute(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.SYSTEM_FAILED);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.NONE);
              assertThat(result.errors())
                  .singleElement()
                  .satisfies(
                      error -> {
                        assertThat(error.errorKey())
                            .isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE);
                        assertThat(error.target()).isEqualTo("PAGE");
                      });
              assertThat(result.csrfToken()).isNull();
              assertThat(result.authFlowSessionId()).isNull();
            })
        .expectComplete()
        .verify(WAIT);

    assertThat(captcha.consumed.get()).isEqualTo(1);
    assertThat(events).containsSubsequence("captcha.load", "captcha.consume", "registry.resolve");
    assertThat(registry.calls.get()).isEqualTo(1);
    assertThat(registry.lastType.get()).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    assertThat(registry.lastLocale.get()).isEqualTo("en");
    verifyNoInteractions(delivery);
  }

  @Test
  void aConfiguredDefinitionWithTheSameFixturesReachesTheStartedResult() {
    Registry registry = Registry.configured(events);
    CaptchaPort captcha = new CaptchaPort(events);
    MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    RegistrationStartProtectedOperation operation =
        operation(captcha, new RegistrationMessageDeliveryAdapter(delivery, registry));

    StepVerifier.create(operation.execute(command()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .expectComplete()
        .verify(WAIT);

    assertThat(captcha.consumed.get()).isEqualTo(1);
    assertThat(registry.calls.get()).isEqualTo(1);
    verify(delivery).deliver(any());
  }

  // ----- fixtures ---------------------------------------------------------------------------

  private static RegistrationStartProtectedOperation operation(
      CaptchaPort captcha, RegistrationMessagePort messages) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new RegistrationStartProtectedOperation(
        new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
        new Lookup(),
        new Sessions(),
        sessionId -> Mono.just("csrf_token_safe"),
        new Otp(),
        messages,
        new Policy(),
        new RegistrationCaptchaGateService(captcha, clock),
        clock);
  }

  private static StartRegistrationCommand command() {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey("idem_abc123"),
        "GPFS",
        "1234567890",
        "31/01/1980",
        "member@example.com",
        null,
        CHALLENGE_ID.value(),
        null,
        "en",
        "corr-secret",
        Duration.ofMinutes(5));
  }

  private static CaptchaChallenge verifiedChallenge() {
    return new CaptchaChallenge(
        CHALLENGE_ID,
        new CaptchaAnswerHash(ANSWER_HASH),
        CaptchaStatus.VERIFIED,
        0,
        5,
        NOW.minusSeconds(30),
        NOW.plusSeconds(300),
        NOW.minusSeconds(5),
        null,
        null,
        null);
  }

  /** The production setting: the pre-start challenge is on and its type is CAPTCHA. */
  private static final class Policy implements AuthWorkflowPolicyPort {
    @Override
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
      return new RegistrationAuthWorkflowPolicy(
          new PreStartChallengePolicy(true, PreStartChallengeType.CAPTCHA),
          new CaptchaPolicy(
              7, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", true, 300, 5),
          null,
          null,
          null);
    }

    @Override
    public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
      return Optional.empty();
    }
  }

  /** Holds one VERIFIED, unexpired challenge and records what the gate does with it. */
  private static final class CaptchaPort implements CaptchaChallengePort {
    private final List<String> events;
    final AtomicInteger consumed = new AtomicInteger();
    private CaptchaChallenge challenge = verifiedChallenge();

    CaptchaPort(List<String> events) {
      this.events = events;
    }

    @Override
    public Mono<CaptchaChallenge> create(CaptchaChallenge value) {
      challenge = value;
      return Mono.just(value);
    }

    @Override
    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId id) {
      events.add("captcha.load");
      return Mono.just(Optional.ofNullable(challenge));
    }

    @Override
    public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId id, Instant at) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId id, Instant at) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId id, Instant at) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId id, Instant at) {
      events.add("captcha.consume");
      consumed.incrementAndGet();
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
              at,
              challenge.refreshedAt(),
              challenge.lockedAt());
      return Mono.just(challenge);
    }

    @Override
    public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId id, Instant at) {
      return Mono.just(challenge);
    }

    @Override
    public Mono<Void> delete(CaptchaChallengeId id) {
      challenge = null;
      return Mono.empty();
    }
  }

  /** Records what the adapter asks for; a missing definition throws like the real registry. */
  private static final class Registry implements NoticeTemplateRegistryPort {
    final boolean absent;
    final List<String> events;
    final AtomicInteger calls = new AtomicInteger();
    final AtomicReference<MessageDeliveryType> lastType = new AtomicReference<>();
    final AtomicReference<MessageDeliveryChannel> lastChannel = new AtomicReference<>();
    final AtomicReference<String> lastLocale = new AtomicReference<>();

    private Registry(boolean absent, List<String> events) {
      this.absent = absent;
      this.events = events;
    }

    static Registry missing(List<String> events) {
      return new Registry(true, events);
    }

    static Registry configured(List<String> events) {
      return new Registry(false, events);
    }

    @Override
    public NoticeTemplateDefinition resolve(
        MessageDeliveryType messageType, MessageDeliveryChannel channel, String locale) {
      events.add("registry.resolve");
      calls.incrementAndGet();
      lastType.set(messageType);
      lastChannel.set(channel);
      lastLocale.set(locale);
      if (absent) {
        throw new IllegalStateException(MISSING_MESSAGE);
      }
      return new NoticeTemplateDefinition(
          messageType,
          channel,
          "en",
          channel == MessageDeliveryChannel.EMAIL ? "Registration OTP" : null,
          "Code {code} valid {ttlValue} {ttlUnit}",
          Set.of("code", "ttlValue", "ttlUnit"),
          "minutes");
    }
  }

  private static final class Lookup implements RegistrationLookupPort {
    @Override
    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      return Mono.just(
          new RegistrationLookupResult(
              RegistrationLookupStatus.ELIGIBLE,
              List.of(new RegistrationLookupAccount("OI", "JPM", null, null, null, null, null, Boolean.FALSE, null)),
              null));
    }
  }

  private static final class Sessions implements RegistrationSessionPort {
    @Override
    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      return Mono.just("afs_session_for_cookie");
    }

    @Override
    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      return Mono.empty();
    }
  }

  private static final class Otp implements RegistrationOtpPort {
    @Override
    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.just(new RegistrationOtpCreationResult("otp_internal", "123456", 300, 60, 3));
    }

    @Override
    public Mono<RegistrationOtpCreationResult> resendOtp(
        String activeId, RegistrationContactType type, String destination) {
      return Mono.just(new RegistrationOtpCreationResult("otp-new", "123456", 300, 60, 4));
    }
  }
}
