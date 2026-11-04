package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.ApimForgotPasswordUsernameLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCommands;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyRecord;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservation;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationToken;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyStatus;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.IdempotentStartForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * Sub-task 2b.2 STEP 1, Batch 2a (Forgot Password Start) characterization. Full chain: real {@code
 * ApimForgotPasswordUsernameLookupAdapter} -> real {@code ForgotPasswordStartProtectedOperation} ->
 * real {@code IdempotentStartForgotPasswordService} wired to a real {@link
 * DefaultIdempotencyExecutor} (backed by a test-only in-memory {@link IdempotencyPort}, mirroring
 * the committed precedent {@code
 * RegistrationMissingDefinitionStartResendIntegrationTest.AcquiringPort}) -> real {@code
 * ForgotPasswordController.startForgotPassword} -> real {@code ForgotPasswordStartWebMapper} ->
 * real {@code ForgotPasswordHttpResponses}. Stubs only {@link ApimExchangeExecutor}.
 */
class ForgotPasswordBatch2aStartCharacterizationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private Logger adapterLogger;
  private Logger operationLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    listAppender = new ListAppender<>();
    listAppender.start();
    adapterLogger = (Logger) LoggerFactory.getLogger(ApimForgotPasswordUsernameLookupAdapter.class);
    adapterLogger.addAppender(listAppender);
    operationLogger = (Logger) LoggerFactory.getLogger(ForgotPasswordStartProtectedOperation.class);
    operationLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    adapterLogger.detachAppender(listAppender);
    operationLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  /**
   * Scenario 1: {@link ApimExchangeExecutor} fails (simulated APIM transport failure) during
   * username lookup. Real chain: adapter's {@code doOnError} logs {@code
   * event=forgot-password.username-lookup.failed} and the error propagates (confirmed,
   * ApimForgotPasswordUsernameLookupAdapter.java); {@code ForgotPasswordStartProtectedOperation
   * .execute}'s {@code onErrorResume} logs {@code event=forgot-password.start.failed
   * stage=username-lookup} and returns a {@code SYSTEM_FAILED} result (confirmed,
   * ForgotPasswordStartProtectedOperation.java); {@code IdempotentStartForgotPasswordService
   * .executeAcquired} sees {@code SYSTEM_FAILED} and converts it to {@code
   * Mono.error(IllegalStateException)} (confirmed); the real {@link DefaultIdempotencyExecutor}, on
   * first acquisition with a failing supplier, propagates that error (mirrors the committed {@code
   * DefaultIdempotencyExecutorTest.protectedOperationFailureUsesClassifierExactlyOnce} precedent);
   * {@code IdempotentStartForgotPasswordService.start} has no {@code onErrorResume} (confirmed), so
   * the error reaches {@code ForgotPasswordController.startForgotPassword}'s {@code onErrorResume},
   * which maps to {@code startMapper.systemUnavailable()} (confirmed).
   *
   * <p>NOTE: this test substitutes a minimal in-memory {@link IdempotencyPort} fake for the real
   * persistence adapter (the real Redis-backed adapter's source was not confirmed in this session);
   * the {@link DefaultIdempotencyExecutor} itself, its constructor, and the full
   * acquire/classify/fail contract are the real production classes, confirmed via
   * DefaultIdempotencyExecutorTest.java and IdempotencyPort.java.
   */
  @Test
  void start_apimTransportFailure_realChainReachesSystemUnavailable() {
    RuntimeException transportFailure = new IllegalStateException("APIM connection reset");
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            return Mono.error(transportFailure);
          }
        };
    var lookupAdapter =
        new ApimForgotPasswordUsernameLookupAdapter(
            executor, Map.of("GPFS", MemberIdentityType.GPFS));

    var operation =
        new ForgotPasswordStartProtectedOperation(
            lookupAdapter,
            neverCalledContextProtector(),
            neverCalledConcurrencyService(),
            neverCalledCsrfUseCase(),
            CLOCK,
            new SessionTtlPolicy(true, true, 1800),
            new SessionConcurrencyPolicy(
                SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING),
            "afs",
            16,
            true,
            true,
            false,
            Map.of("GPFS", MemberIdentityType.GPFS));

    var json = new JacksonTransientJsonAdapter(OBJECT_MAPPER);
    var envelope = new ActionTaggedIdempotencyResponseEnvelope(json);
    var replayCodec =
        new ForgotPasswordStartReplayCodec(
            envelope, neverCalledProtectedReplayPort(), neverCalledCsrfUseCase(), CLOCK);
    var responses = new ForgotPasswordStartExecutionBridge(json, replayCodec, 1800);
    var policy =
        new ForgotPasswordIdempotencyIntegrationPolicy(
            new IdempotencyOperationIdentityFactory(),
            new CanonicalIdempotencyFingerprintFactory());

    var idempotencyExecutor =
        new DefaultIdempotencyExecutor(
            new InMemoryIdempotencyPort(),
            () -> new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
            CLOCK);

    StartForgotPasswordUseCase useCase =
        new IdempotentStartForgotPasswordService(idempotencyExecutor, operation, responses, policy);

    ForgotPasswordController controller = startOnlyController(useCase);

    ResponseEntity<MutationResponse<?>> response =
        (ResponseEntity<MutationResponse<?>>)
            (ResponseEntity<?>)
                controller
                    .startForgotPassword(
                        new ForgotPasswordStartRequest("member1"),
                        "idem-fp-start-001",
                        "req-fp-start-001",
                        "en",
                        null,
                        new MockServerHttpResponse())
                    .block();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    MutationResponse<?> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    JsonNode json2 = OBJECT_MAPPER.valueToTree(body);
    assertThat(json2.at("/errors/0/code").asText()).isNotBlank();

    assertThat(listAppender.list).hasSize(2);
    ILoggingEvent adapterEvent = listAppender.list.get(0);
    assertThat(adapterEvent.getLevel()).isEqualTo(Level.ERROR);
    assertThat(adapterEvent.getFormattedMessage())
        .contains("forgot-password.username-lookup.failed");
    ILoggingEvent operationEvent = listAppender.list.get(1);
    assertThat(operationEvent.getLevel()).isEqualTo(Level.ERROR);
    assertThat(operationEvent.getFormattedMessage()).contains("forgot-password.start.failed");
    assertThat(operationEvent.getFormattedMessage()).contains("stage=username-lookup");
  }

  // ───────────────────────── shared fixtures ─────────────────────────

  private static ForgotPasswordController startOnlyController(StartForgotPasswordUseCase useCase) {
    return new ForgotPasswordController(
        useCase,
        new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        AuthFlowCookieWriter.defaultWriter(),
        null,
        null,
        null,
        null);
  }

  private static ForgotPasswordAuthFlowContextProtectorPort neverCalledContextProtector() {
    return new ForgotPasswordAuthFlowContextProtectorPort() {
      @Override
      public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
        throw new AssertionError("protect() must not be called before the lookup failure");
      }

      @Override
      public ForgotPasswordAuthFlowContext unprotect(
          ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
        throw new AssertionError("unprotect() must not be called before the lookup failure");
      }
    };
  }

  private static AuthFlowSessionConcurrencyService neverCalledConcurrencyService() {
    AuthFlowSessionPort neverCalledPort =
        new AuthFlowSessionPort() {
          @Override
          public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            return Mono.error(
                new AssertionError("session create must not be called before the lookup failure"));
          }

          @Override
          public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
            return Mono.error(
                new AssertionError("session load must not be called before the lookup failure"));
          }

          @Override
          public Mono<AuthFlowSession> update(AuthFlowSession session) {
            return Mono.error(
                new AssertionError("session update must not be called before the lookup failure"));
          }

          @Override
          public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.error(
                new AssertionError("session touch must not be called before the lookup failure"));
          }

          @Override
          public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.error(
                new AssertionError(
                    "session complete must not be called before the lookup failure"));
          }

          @Override
          public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.error(
                new AssertionError("session cancel must not be called before the lookup failure"));
          }

          @Override
          public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
            return Mono.error(
                new AssertionError("session delete must not be called before the lookup failure"));
          }
        };
    SessionIdGenerator idGenerator =
        command -> {
          throw new AssertionError(
              "session id generation must not be called before the lookup failure");
        };
    var lifecycle =
        new AuthFlowSessionLifecycleService(
            neverCalledPort, idGenerator, new SessionTtlCalculator(), CLOCK);
    return new AuthFlowSessionConcurrencyService(lifecycle);
  }

  private static CreateAuthFlowSessionCsrfUseCase neverCalledCsrfUseCase() {
    return command -> {
      throw new AssertionError("CSRF creation must not be called before the lookup failure");
    };
  }

  private static ForgotPasswordStartProtectedReplayPort neverCalledProtectedReplayPort() {
    return new ForgotPasswordStartProtectedReplayPort() {
      @Override
      public Mono<com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference> store(
          ForgotPasswordStartProtectedReplayState state, Duration ttl) {
        return Mono.error(
            new AssertionError(
                "protected replay store must not be called before the lookup failure"));
      }

      @Override
      public Mono<ForgotPasswordStartProtectedReplayState> load(
          com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference reference) {
        return Mono.error(
            new AssertionError(
                "protected replay load must not be called before the lookup failure"));
      }
    };
  }

  /**
   * Minimal in-memory {@link IdempotencyPort} fake, mirroring the committed precedent {@code
   * RegistrationMissingDefinitionStartResendIntegrationTest.AcquiringPort}: always grants the
   * reservation (no prior record exists), so the real {@link DefaultIdempotencyExecutor} runs the
   * protected operation for real.
   */
  private static final class InMemoryIdempotencyPort implements IdempotencyPort {
    @Override
    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve command) {
      return Mono.just(
          new IdempotencyReservation(
              IdempotencyReservationOutcome.ACQUIRED,
              IdempotencyStatus.PROCESSING,
              null,
              command.reservationToken()));
    }

    @Override
    public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete command) {
      return Mono.empty();
    }

    @Override
    public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail command) {
      return Mono.empty();
    }

    @Override
    public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown command) {
      return Mono.empty();
    }

    @Override
    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operationId) {
      return Mono.just(Optional.empty());
    }
  }
}
