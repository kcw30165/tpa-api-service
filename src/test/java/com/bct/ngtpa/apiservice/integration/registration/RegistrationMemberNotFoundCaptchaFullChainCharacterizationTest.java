package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * Sub-task 2b.2 STEP 1, Priority 1 item 1 (2026-10-28 coverage-matrix decision; origin downgraded
 * 2026-10-30 per E1): Registration member-not-found, full chain, captcha enabled and disabled.
 * Extends {@code RegistrationBatch1FullChainCharacterizationTest}'s proven fixture pattern (real
 * {@link RegistrationStartProtectedOperation}, real {@link RegistrationController}, stubbing only
 * {@link ApimExchangeExecutor}); that existing class's two captcha on/off tests both reach the
 * ELIGIBLE (success) path only and do not cover member-not-found - this class fills that specific
 * gap.
 *
 * <p>Uses the REAL {@link StructuredFailureEventLogger} as the {@code FailureEventPort}
 * implementation (not a mock), with a {@link ListAppender} attached, per instruction. {@code
 * FailureOrigin.DOWNSTREAM_BUSINESS} is confirmed (via {@code StructuredFailureEventLogger}'s real
 * {@code WARN_ORIGINS} set, which includes it, and the no-cause {@code publish(event)} overload
 * being used) to log at WARN. This is an intentional origin reclassification (E1): a recognized
 * downstream business rejection (member not found) is an expected business outcome, not a
 * downstream contract/transport defect, and was previously misclassified as {@code
 * DOWNSTREAM_CONTRACT} (ERROR).
 *
 * <p>Confirmed real routing chain for this condition: {@code TrpSearchMemberApimResultMapper}
 * (lines 22-24) maps the recognized err-message {@code err.tpa.member.not.found} to {@code
 * RegistrationLookupResult(NOT_ELIGIBLE, [], null)} - a normal result, not an exception. {@code
 * RegistrationStartProtectedOperation.handleLookup} (lines 179-185) routes {@code NOT_ELIGIBLE} to
 * {@code StartRegistrationResultStatus.VERIFICATION_FAILED} with error target {@code
 * "identityValue"}. {@code RegistrationStartWebMapper.toWorkflowErrorType} (lines 201-213): because
 * the target is non-null and not {@code "PAGE"}, the target-check short-circuits BEFORE the status
 * switch, so the resulting {@code WorkflowErrorType} is {@code FIELD}, not {@code BUSINESS} (the
 * value the switch's {@code VERIFICATION_FAILED} case would otherwise produce). {@code
 * toWorkflowStatus} maps {@code VERIFICATION_FAILED} to {@code
 * WorkflowResultStatus.BUSINESS_FAILED}, which {@code WorkflowMutationResponseMapper.toApiStatus}
 * (line 35) maps to {@code ApiStatus.BUSINESS_REJECTED}, which {@code
 * RegistrationHttpResponses.status} (lines 66-69) maps to {@code HttpStatus.UNPROCESSABLE_CONTENT}
 * (422). None of this public-response routing changes under E1; only the internal failure-event
 * origin and log level change.
 */
class RegistrationMemberNotFoundCaptchaFullChainCharacterizationTest {

  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private Logger failureEventLoggerLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    listAppender = new ListAppender<>();
    listAppender.start();
    failureEventLoggerLogger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    failureEventLoggerLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    failureEventLoggerLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  // ───────── Scenario 1 of 2: captcha ENABLED, member not found ─────────

  @Test
  void registrationStart_captchaEnabled_memberNotFound_realChainReachesBusinessRejected422()
      throws Exception {
    String challengeId = "cap_scenario1_enabled";
    InMemoryCaptchaPort captchaPort = new InMemoryCaptchaPort();
    captchaPort.seedVerified(challengeId);

    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    var lookup = memberNotFoundLookupAdapter(failureEventLogger);
    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaEnabledPolicy(),
            new RegistrationCaptchaGateService(captchaPort, CLOCK),
            CLOCK);

    StartRegistrationUseCase useCase = operation::execute;
    RegistrationController controller =
        startOnlyController(useCase, new RegistrationStartWebMapper(Duration.ofMinutes(5)));

    ResponseEntity<?> response =
        controller
            .startRegistration(
                new RegistrationStartRequest(
                    "GPFS", "1234567890", "31/01/1980", "member@example.com", null, challengeId),
                "idem-member-not-found-captcha-enabled-001",
                "req-member-not-found-captcha-enabled-001",
                "en",
                null,
                new MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    MutationResponse<?> body = (MutationResponse<?>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("FIELD");
    assertThat(error.code()).isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED.value());
    assertThat(error.targets()).isEqualTo(List.of("identityValue"));

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    JsonNode payload = new ObjectMapper().readTree(event.getFormattedMessage());
    assertThat(payload.get("event").asText())
        .isEqualTo("event.registration.lookup.downstream_rejection");
    assertThat(payload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_BUSINESS");
    assertThat(payload.get("internalCode").asText()).isEqualTo("err.tpa.member.not.found");
    assertThat(payload.get("publicCode").asText())
        .isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED.value());
    assertThat(payload.get("httpStatus").asInt()).isEqualTo(422);
    assertThat(payload.get("retryable").asBoolean()).isFalse();
  }

  @Test
  void registrationStart_captchaDisabled_memberNotFound_realChainReachesBusinessRejected422()
      throws Exception {
    var loggingSanitizer =
        new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()));
    var failureEventLogger = new StructuredFailureEventLogger(loggingSanitizer);

    var lookup = memberNotFoundLookupAdapter(failureEventLogger);
    var operation =
        new RegistrationStartProtectedOperation(
            new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
            lookup,
            recordingSessionPort(),
            fixedCsrfPort(),
            recordingOtpPort(),
            acceptingMessagePort(),
            captchaDisabledPolicy(),
            new RegistrationCaptchaGateService(new InMemoryCaptchaPort(), CLOCK),
            CLOCK);

    StartRegistrationUseCase useCase = operation::execute;
    RegistrationController controller =
        startOnlyController(useCase, new RegistrationStartWebMapper(Duration.ofMinutes(5)));

    ResponseEntity<?> response =
        controller
            .startRegistration(
                new RegistrationStartRequest(
                    "GPFS", "1234567890", "31/01/1980", "member@example.com", null, null),
                "idem-member-not-found-captcha-disabled-001",
                "req-member-not-found-captcha-disabled-001",
                "en",
                null,
                new MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    MutationResponse<?> body = (MutationResponse<?>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(body.errors()).hasSize(1);
    ApiError error = body.errors().getFirst();
    assertThat(error.type()).isEqualTo("FIELD");
    assertThat(error.code()).isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED.value());
    assertThat(error.targets()).isEqualTo(List.of("identityValue"));

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    JsonNode payload = new ObjectMapper().readTree(event.getFormattedMessage());
    assertThat(payload.get("event").asText())
        .isEqualTo("event.registration.lookup.downstream_rejection");
    assertThat(payload.get("failureOrigin").asText()).isEqualTo("DOWNSTREAM_BUSINESS");
    assertThat(payload.get("internalCode").asText()).isEqualTo("err.tpa.member.not.found");
    assertThat(payload.get("publicCode").asText())
        .isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED.value());
    assertThat(payload.get("httpStatus").asInt()).isEqualTo(422);
    assertThat(payload.get("retryable").asBoolean()).isFalse();
  }

  // ───────── shared fixtures ─────────

  private static ApimRegistrationLookupAdapter memberNotFoundLookupAdapter(
      StructuredFailureEventLogger failureEventLogger) {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<TrpSearchMemberApimDataItem> responseBody =
                new ApimResponseBody<>("err.tpa.member.not.found", List.of());
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(responseBody);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    return new ApimRegistrationLookupAdapter(
        executor, failureEventLogger, new MaskingService(new MaskingProperties()));
  }

  private static RegistrationController startOnlyController(
      StartRegistrationUseCase useCase, RegistrationStartWebMapper mapper) {
    return new RegistrationController(
        useCase,
        mapper,
        AuthFlowCookieWriter.defaultWriter(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static RegistrationSessionPort recordingSessionPort() {
    return new RegistrationSessionPort() {
      @Override
      public Mono<String> createRegistrationSession(
          String maskedDestination, RegistrationContactType contactType) {
        return Mono.error(
            new AssertionError("session creation must not be invoked for a NOT_ELIGIBLE lookup"));
      }

      @Override
      public Mono<Void> updateOtpChallenge(
          String authFlowSessionId,
          String activeOtpChallengeId,
          RegistrationContactType contactType,
          String maskedDestination) {
        return Mono.error(
            new AssertionError(
                "OTP challenge update must not be invoked for a NOT_ELIGIBLE lookup"));
      }
    };
  }

  private static RegistrationCsrPort fixedCsrfPort() {
    return authFlowSessionId ->
        Mono.error(
            new AssertionError("CSRF binding must not be invoked for a NOT_ELIGIBLE lookup"));
  }

  private static RegistrationOtpPort recordingOtpPort() {
    return (contactType, destination) ->
        Mono.error(
            new AssertionError("OTP creation must not be invoked for a NOT_ELIGIBLE lookup"));
  }

  private static RegistrationMessagePort acceptingMessagePort() {
    return (contactType, destination, code, ttlSeconds, language, correlationId) ->
        Mono.error(
            new AssertionError("message delivery must not be invoked for a NOT_ELIGIBLE lookup"));
  }

  private static AuthWorkflowPolicyPort captchaEnabledPolicy() {
    return policyWith(new PreStartChallengePolicy(true, PreStartChallengeType.CAPTCHA));
  }

  private static AuthWorkflowPolicyPort captchaDisabledPolicy() {
    return policyWith(new PreStartChallengePolicy(false, PreStartChallengeType.CAPTCHA));
  }

  private static AuthWorkflowPolicyPort policyWith(PreStartChallengePolicy preStartChallenge) {
    return new AuthWorkflowPolicyPort() {
      @Override
      public RegistrationAuthWorkflowPolicy registrationPolicy() {
        return new RegistrationAuthWorkflowPolicy(
            preStartChallenge,
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
    };
  }

  private static final class InMemoryCaptchaPort implements CaptchaChallengePort {
    private final Map<CaptchaChallengeId, CaptchaChallenge> values = new ConcurrentHashMap<>();

    void seedVerified(String id) {
      var challengeId = new CaptchaChallengeId(id);
      values.put(
          challengeId,
          new CaptchaChallenge(
              challengeId,
              new CaptchaAnswerHash("0".repeat(64)),
              CaptchaStatus.VERIFIED,
              0,
              5,
              NOW.minusSeconds(30),
              NOW.plusSeconds(300),
              NOW.minusSeconds(5),
              null,
              null,
              null));
    }

    @Override
    public Mono<CaptchaChallenge> create(CaptchaChallenge challenge) {
      values.put(challenge.captchaChallengeId(), challenge);
      return Mono.just(challenge);
    }

    @Override
    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId id) {
      return Mono.just(Optional.ofNullable(values.get(id)));
    }

    @Override
    public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId id, Instant at) {
      CaptchaChallenge old = values.get(id);
      CaptchaChallenge consumed =
          new CaptchaChallenge(
              old.captchaChallengeId(),
              old.answerHash(),
              CaptchaStatus.CONSUMED,
              old.failedAttemptCount(),
              old.maxAttempts(),
              old.createdAt(),
              old.expiresAt(),
              old.verifiedAt(),
              at,
              old.refreshedAt(),
              old.lockedAt());
      values.put(id, consumed);
      return Mono.just(consumed);
    }

    @Override
    public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    @Override
    public Mono<Void> delete(CaptchaChallengeId id) {
      values.remove(id);
      return Mono.empty();
    }
  }
}
