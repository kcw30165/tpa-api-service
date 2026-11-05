package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationCompletionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionPreDispatchException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.RegistrationCompleteProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.RegistrationCompleteProtectedOperationInput;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-task 2b.2 STEP 1, Batch 1 (Registration) characterization pack. Full-chain rows stub only
 * {@link ApimExchangeExecutor}; every other layer (adapter, protected operation, controller,
 * mapper, {@code RegistrationHttpResponses}) is the real production class, confirmed against actual
 * source by scripts 001, 002, 003, 005, 006, 007 (read-only investigations).
 *
 * <p><b>Idempotency wrapper bypass (both rows below).</b> Per Nick's 2026-10-27 decision, {@code
 * StartRegistrationUseCase}/{@code CompleteRegistrationUseCase} are wired directly as {@code
 * operation::execute}, bypassing {@code SharedStartRegistrationService}/{@code
 * SharedCompleteRegistrationService}/{@code DefaultIdempotencyExecutor}. Evidence that the wrapper
 * passes failures through unchanged (script 003, confirmed by direct source read, not re-verified
 * by this test): {@code RegistrationStartSharedExecutionBridge.java:54,68}'s only two catches are
 * {@code JsonProcessingException} inside {@code toTransientResponse}/{@code fromTransientResponse}
 * wire encode/decode helpers, unrelated to the {@code operation.execute(command)} failure path;
 * {@code SharedCompleteRegistrationService.java:124,226}'s catches are in {@code
 * usernames.load(session)} (before the protected operation runs) and {@code sha256(...)}, also
 * unrelated; {@code DefaultIdempotencyExecutor.classifyProtectedFailureThenError} (an {@code
 * onErrorResume}) and {@code markUnknownThenError} both terminate in {@code
 * Mono.error(failure)}/{@code Mono.error(original)}, preserving the original exception whenever the
 * persistence calls themselves succeed. Replay/conflict behaviour is explicitly out of scope
 * ({@code RegistrationStartWorkflowIntegrationTest} covers it).
 */
class RegistrationBatch1FullChainCharacterizationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private Logger handlerLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    listAppender = new ListAppender<>();
    listAppender.start();
    handlerLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    handlerLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    handlerLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  // ───────────────────────── Registration lookup (captcha ENABLED) ─────────────────────────

  @Test
  void registrationStart_captchaEnabled_realChainReachesStartedResponse() {
    String challengeId = "cap_0000000000000001";
    InMemoryCaptchaPort captchaPort = new InMemoryCaptchaPort();
    captchaPort.seedVerified(challengeId);

    var lookup = lookupAdapter();
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
                "idem-captcha-enabled-001",
                "req-captcha-enabled-001",
                "en",
                null,
                new MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MutationResponse<?> body = (MutationResponse<?>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isTrue();
    JsonNode json = OBJECT_MAPPER.valueToTree(body);
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("VERIFY_OTP");
    assertThat(json.at("/result/data/csrfToken").asText()).isNotBlank();
    assertThat(captchaPort.require(challengeId).status()).isEqualTo(CaptchaStatus.CONSUMED);
  }

  // ───────────────────────── Registration lookup (captcha DISABLED) ─────────────────────────

  @Test
  void registrationStart_captchaDisabled_realChainSkipsCaptchaAndReachesStartedResponse() {
    var lookup = lookupAdapter();
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
                "idem-captcha-disabled-001",
                "req-captcha-disabled-001",
                "en",
                null,
                new MockServerHttpResponse())
            .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MutationResponse<?> body = (MutationResponse<?>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isTrue();
    JsonNode json = OBJECT_MAPPER.valueToTree(body);
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("VERIFY_OTP");
  }

  // ───────────────────────── Registration completion catch-all ─────────────────────────

  /**
   * Proves the full chain for the central-handler catch-all row in {@code
   * 07-apim-error-classification.md} ("Registration -- ApimRegistrationCompletionAdapter
   * (TRPRegMember)", {@code ApimProfileResolutionException} during completion). Step 1 captures the
   * real {@code Mono} error from the real {@code ApimRegistrationCompletionAdapter} wired through
   * the real {@code RegistrationCompleteProtectedOperation} (stubbing only {@code
   * ApimExchangeExecutor}); step 2 feeds that captured exception into a real {@code
   * ApiExceptionHandler}, built identically to {@code ApiExceptionHandlerCharacterizationTest}'s
   * {@code setUp()} (script 006 confirmed this exact template), and asserts the final {@code
   * ResponseEntity}/body/log line. {@code RegistrationController.completeRegistration}'s try/catch
   * (script 007 confirmed lines 134/156 by direct source read) wraps only the synchronous
   * command-construction/Mono-chain-registration statements; it cannot catch an error signal
   * emitted later, inside the returned {@code Mono}, which is exactly this failure's path -- this
   * is why it resolves via {@code CatchAllExceptionMapping} and not the controller's own catch.
   *
   * <p>{@code RegistrationCompleteProtectedOperation.invoke()} calls {@code
   * sessionTransitions.transition(session, COMPLETING, now, false)} <em>before</em> {@code
   * dispatch()} ever calls {@code completion.complete(...)} (script 007's full source dump). The
   * existing {@code RegistrationCompleteProtectedOperationTest} proves this transition step only
   * requires {@code AuthFlowSessionPort.loadSnapshot(...)} and {@code
   * AuthFlowSessionPort.updateRevisioned(...)} to be stubbed (it mocks exactly these two methods
   * and nothing about the internal {@code RevisionAwareAuthFlowSessionWriter} collaborator), so
   * this test mirrors that same stubbing rather than guessing at that collaborator's internals.
   */
  @Test
  void registrationCompletion_profileResolutionFailure_realChainReachesCatchAllCentralHandler() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            return Mono.error(
                new ApimProfileResolutionException("no mapping for accountEnv DB-pre-dispatch"));
          }
        };
    var completionAdapter = new ApimRegistrationCompletionAdapter(executor);

    var sessionId = new AuthFlowSessionId("afs_completion_catchall");
    var session = usernameVerifiedSession(sessionId);
    var operation =
        new RegistrationCompleteProtectedOperation(
            sessionPortReturning(session),
            alwaysAcquiringLockPort(),
            completionAdapter,
            neverCalledLookupPort(),
            neverCalledCancellationPort(),
            Duration.ofSeconds(10),
            CLOCK);

    var command =
        new CompleteRegistrationCommand(
            sessionId,
            "csrf-safe",
            new RegistrationIdempotencyKey("idem-completion-catchall"),
            "Password1!",
            "Password1!",
            "https://portal.test",
            "https://portal.test/register",
            "en",
            "req-completion-catchall",
            Duration.ofMinutes(5));
    var input =
        new RegistrationCompleteProtectedOperationInput(
            command,
            new RegistrationAuthFlowContext(
                MemberIdentityType.GPFS,
                "1234567890",
                "31/01/1980",
                RegistrationContactType.EMAIL,
                "member@example.com"),
            new UsernameCheckState(new SelectedTrust("JPM", "JP"), "member1", NOW, null),
            session);

    AtomicReference<Throwable> captured = new AtomicReference<>();
    StepVerifier.create(operation.execute(input)).expectErrorSatisfies(captured::set).verify();

    Throwable failure = captured.get();
    assertThat(failure).isInstanceOf(RegistrationCompletionPreDispatchException.class);
    assertThat(failure.getMessage()).doesNotContain("accountEnv", "no mapping", "profile");

    ApiExceptionHandler handler =
        new ApiExceptionHandler(
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new PublicMessageResolver(
                (errorCode, locale, accountEnv, trustCode, schemeType) ->
                    "message for " + errorCode),
            new ErrorEventLogger(
                new com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer(
                    OBJECT_MAPPER, new MaskingService(new MaskingProperties()))),
            new ErrorResponseRenderer());

    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/registration/complete"));
    exchange
        .getAttributes()
        .put(
            com.bct.ngtpa.apiservice.shared.web.RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY,
            "req-completion-catchall-handler");

    ResponseEntity<MutationResponse<Void>> response = handler.handle((Exception) failure, exchange);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    MutationResponse<Void> mutation = response.getBody();
    assertThat(mutation).isNotNull();
    assertThat(mutation.success()).isFalse();
    assertThat(mutation.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(mutation.errors()).hasSize(1);
    ApiError error = mutation.errors().getFirst();
    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo(ErrorCodes.SYSTEM_UNEXPECTED);
    assertThat(error.source()).isEqualTo("SERVER");

    assertThat(listAppender.list).hasSize(1);
    ILoggingEvent event = listAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNotNull();
  }

  // ───────────────────────── shared fixtures ─────────────────────────

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

  private static ApimRegistrationLookupAdapter lookupAdapter() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          @SuppressWarnings("unchecked")
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            var item =
                new TrpSearchMemberApimDataItem(
                    "JP", "JPM", null, null, null, null, null, Boolean.FALSE, null);
            ApimResponseBody<TrpSearchMemberApimDataItem> body =
                new ApimResponseBody<>("", List.of(item));
            ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope =
                new ApimResponseEnvelope<>(body);
            return (Mono<ApimResponseEnvelope<D>>) (Mono<?>) Mono.just(envelope);
          }
        };
    return new ApimRegistrationLookupAdapter(
        executor, noopFailureEventPort(), new MaskingService(new MaskingProperties()));
  }

  private static FailureEventPort noopFailureEventPort() {
    return event -> {};
  }

  private static RegistrationSessionPort recordingSessionPort() {
    return new RegistrationSessionPort() {
      @Override
      public Mono<String> createRegistrationSession(
          String maskedDestination, RegistrationContactType contactType) {
        return Mono.just("sid-batch1");
      }

      @Override
      public Mono<Void> updateOtpChallenge(
          String authFlowSessionId,
          String activeOtpChallengeId,
          RegistrationContactType contactType,
          String maskedDestination) {
        return Mono.empty();
      }
    };
  }

  private static RegistrationCsrPort fixedCsrfPort() {
    return authFlowSessionId -> Mono.just("csrf-token-batch1");
  }

  private static RegistrationOtpPort recordingOtpPort() {
    return (contactType, destination) ->
        Mono.just(new RegistrationOtpCreationResult("otp-batch1", "123456", 300, 60, 5));
  }

  private static RegistrationMessagePort acceptingMessagePort() {
    return (contactType, destination, code, ttlSeconds, language, correlationId) ->
        Mono.just(Boolean.TRUE);
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

    CaptchaChallenge require(String id) {
      CaptchaChallenge challenge = values.get(new CaptchaChallengeId(id));
      if (challenge == null) {
        throw new AssertionError("captcha challenge not found: " + id);
      }
      return challenge;
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

  private static AuthFlowSession usernameVerifiedSession(AuthFlowSessionId sessionId) {
    return new AuthFlowSession(
        sessionId,
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.USERNAME_VERIFIED,
        "csrf-hash",
        null,
        new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of()),
        NOW.minusSeconds(600),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private static AuthFlowSessionPort sessionPortReturning(AuthFlowSession session) {
    return new AuthFlowSessionPort() {
      @Override
      public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
        return Mono.error(new UnsupportedOperationException("not needed for this test"));
      }

      @Override
      public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
        return Mono.just(Optional.of(session));
      }

      @Override
      public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(
          AuthFlowSessionId authFlowSessionId) {
        return Mono.just(
            Optional.of(
                new AuthFlowSessionSnapshot(
                    session, new SessionRevision("11111111111111111111111111111111"))));
      }

      @Override
      public Mono<AuthFlowSessionMutationResult> updateRevisioned(
          RevisionedAuthFlowSessionMutationCommand command) {
        return Mono.just(
            new AuthFlowSessionMutationResult(
                AuthFlowSessionMutationOutcome.APPLIED,
                new AuthFlowSessionSnapshot(
                    command.updated(), new SessionRevision("22222222222222222222222222222222"))));
      }
    };
  }

  private static AuthFlowSessionCompletionLockPort alwaysAcquiringLockPort() {
    return new AuthFlowSessionCompletionLockPort() {
      @Override
      public Mono<SessionLockHandle> acquire(AuthFlowSessionId sessionId, Duration lease) {
        return Mono.just(new SessionLockHandle(true, sessionId.value() + "-lock", "owner-token"));
      }

      @Override
      public Mono<Boolean> release(SessionLockHandle handle) {
        return Mono.just(Boolean.TRUE);
      }
    };
  }

  private static RegistrationLookupPort neverCalledLookupPort() {
    return command ->
        Mono.error(new AssertionError("lookup must not be called before the pre-dispatch failure"));
  }

  private static AuthFlowSessionCancellationPort neverCalledCancellationPort() {
    return (sessionId, reason, now) ->
        Mono.error(
            new AssertionError("cancellation must not be called before the pre-dispatch failure"));
  }
}
