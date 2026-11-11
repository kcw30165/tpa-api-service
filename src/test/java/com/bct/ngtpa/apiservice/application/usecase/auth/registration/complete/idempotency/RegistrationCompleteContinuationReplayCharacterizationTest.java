package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestHeaderContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
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
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCommands;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyRecord;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservation;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationToken;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionPreDispatchException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationUsernameCheckContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Characterizes same-key retry after Registration succeeded but its continuation lookup failed. */
class RegistrationCompleteContinuationReplayCharacterizationTest {
  private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
  private static final AuthFlowSessionId SID = new AuthFlowSessionId("afs-continuation");

  private final RegistrationProtectedAuthFlowContextLoader contexts =
      mock(RegistrationProtectedAuthFlowContextLoader.class);
  private final AuthFlowSessionExpiryRefreshPort expiry =
      mock(AuthFlowSessionExpiryRefreshPort.class);
  private final RegistrationUsernameCheckContextLoader usernames =
      mock(RegistrationUsernameCheckContextLoader.class);
  private final RegistrationPasswordValidator passwords = mock(RegistrationPasswordValidator.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionCompletionLockPort locks =
      mock(AuthFlowSessionCompletionLockPort.class);
  private final RegistrationCompletionPort completion = mock(RegistrationCompletionPort.class);
  private final RegistrationLookupPort lookup = mock(RegistrationLookupPort.class);
  private final AuthFlowSessionCancellationPort cancellation =
      mock(AuthFlowSessionCancellationPort.class);
  private final FailureEventPort failureEvents = mock(FailureEventPort.class);
  private final AtomicReference<AuthFlowSession> sessionState = new AtomicReference<>(session());
  private final InMemoryIdempotencyPort persistence = new InMemoryIdempotencyPort();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private SharedCompleteRegistrationService service;

  @BeforeEach
  void setUp() {
    var registrationContext =
        new RegistrationAuthFlowContext(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "test@example.com");
    var username =
        new UsernameCheckState(
            new SelectedTrust("JPM", "OI"), "myusername", NOW, NOW.plusSeconds(600));

    when(contexts.load(any()))
        .thenAnswer(
            ignored ->
                Mono.just(
                    sessionState.get().status() == AuthFlowStatus.USERNAME_VERIFIED
                            || sessionState.get().status() == AuthFlowStatus.COMPLETING
                        ? RegistrationProtectedAuthFlowContextResult.valid(
                            sessionState.get(),
                            "challenge-1",
                            "t***@example.com",
                            registrationContext)
                        : RegistrationProtectedAuthFlowContextResult.invalid(
                            RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS)));
    when(expiry.refreshUsernameVerified(eq(SID), any()))
        .thenAnswer(ignored -> Mono.just(sessionState.get()));
    when(usernames.load(any())).thenReturn(username);
    when(passwords.validate(any(), any(), any()))
        .thenReturn(RegistrationPasswordValidationResult.success());

    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock", "owner")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    when(sessions.load(SID)).thenAnswer(ignored -> Mono.just(Optional.of(sessionState.get())));
    when(sessions.loadSnapshot(SID))
        .thenAnswer(
            ignored ->
                Mono.just(
                    Optional.of(
                        new AuthFlowSessionSnapshot(
                            sessionState.get(),
                            new SessionRevision("11111111111111111111111111111111")))));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation -> {
              RevisionedAuthFlowSessionMutationCommand command = invocation.getArgument(0);
              sessionState.set(command.updated());
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          sessionState.get(),
                          new SessionRevision("22222222222222222222222222222222"))));
            });

    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.registered("20261109", "120000")));
    var lookupFailure = new IllegalStateException("continuation unavailable");
    when(lookup.searchMember(any())).thenReturn(Mono.error(lookupFailure));
    when(lookup.classifySearchMemberFailure(same(lookupFailure)))
        .thenReturn(FailureOrigin.DOWNSTREAM_TRANSPORT);

    var executor =
        new DefaultIdempotencyExecutor(
            persistence,
            () -> new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
            Clock.fixed(NOW, ZoneOffset.UTC));
    var responses =
        new RegistrationCompleteSharedExecutionBridge(new RegistrationCompleteReplayCodec());
    var operation =
        new RegistrationCompleteProtectedOperation(
            sessions,
            locks,
            completion,
            lookup,
            cancellation,
            failureEvents,
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
    service =
        new SharedCompleteRegistrationService(
            contexts,
            expiry,
            usernames,
            passwords,
            executor,
            operation,
            responses,
            new RegistrationSharedIdempotencyIdentityFactory(),
            new RegistrationSharedFingerprintFactory(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void sameKeyRetryReplaysCompletedContinuationFailureWithoutRepeatingSideEffects() {
    CompleteRegistrationCommand command = command();

    CompleteRegistrationResult first = service.complete(command).block();
    CompleteRegistrationResult differentRequest =
        service.complete(command("DifferentPass1!")).block();
    CompleteRegistrationResult replay = service.complete(command).block();

    assertThat(first.status())
        .isEqualTo(CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED);
    assertThat(differentRequest.status())
        .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
    assertThat(differentRequest.errors())
        .extracting(error -> error.code())
        .containsExactly(RegistrationErrorKey.AUTH_FLOW_INVALID.value());
    assertThat(replay.status())
        .isEqualTo(CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED);
    assertThat(sessionState.get().status()).isEqualTo(AuthFlowStatus.COMPLETING);
    assertThat(persistence.storedRecord.status()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThat(persistence.completeCalls).isOne();
    assertThat(persistence.reserveCalls).isOne();
    assertThat(persistence.loadCalls).isEqualTo(2);
    verify(completion, times(1)).complete(any());
    verify(lookup, times(1)).searchMember(any());
    verify(failureEvents, times(1)).publish(any(FailureEvent.class));
    verify(failureEvents, org.mockito.Mockito.never())
        .publish(any(FailureEvent.class), any(Throwable.class));
    verify(locks, times(1)).acquire(eq(SID), any());
    verify(expiry, times(1)).refreshUsernameVerified(eq(SID), any());
  }

  @Test
  void controllerReturnsStructurallyIdenticalHttpResponsesForCompletedReplay() throws Exception {
    RegistrationController controller = registrationController();
    CapturedHttpResponse first = completeThroughController(controller, "request-first-123");
    CapturedHttpResponse replay = completeThroughController(controller, "request-replay-456");

    assertThat(first.statusCode()).isEqualTo(502);
    assertThat(replay.statusCode()).isEqualTo(502);
    assertThat(first.body()).isEqualTo(replay.body());
    assertThat(first.body().path("status").asText()).isEqualTo(ApiStatus.SYSTEM_ERROR.name());
    assertThat(first.body().at("/errors/0/code").asText())
        .isEqualTo(RegistrationErrorKey.REGISTRATION_COMPLETE_CONTINUATIONUNAVAILABLE.value());
    assertThat(first.body().at("/errors/0/type").asText()).isEqualTo("BUSINESS");
    assertThat(first.body().at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
    assertThat(first.body().at("/errors/0/message").isMissingNode()).isTrue();
    assertThat(first.body().at("/result/nextStep").asText()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(first.requestId()).isEqualTo("request-first-123");
    assertThat(replay.requestId()).isEqualTo("request-replay-456");
    assertThat(first.authFlowCookie()).isEqualTo(SID.value());
    assertThat(replay.authFlowCookie()).isEqualTo(SID.value());

    assertThat(persistence.reserveCalls).isOne();
    assertThat(persistence.completeCalls).isOne();
    assertThat(persistence.loadCalls).isOne();
    verify(completion, times(1)).complete(any());
    verify(lookup, times(1)).searchMember(any());
  }

  @Test
  void preDispatchFailureIsProvenFailedAndSameKeyRetryDoesNotResubmit() {
    when(completion.complete(any()))
        .thenReturn(
            Mono.error(
                new RegistrationCompletionPreDispatchException(
                    new IllegalStateException("profile mapping failed"))));

    assertThatThrownBy(() -> service.complete(command()).block())
        .isInstanceOf(RegistrationCompletionPreDispatchException.class);

    assertThat(persistence.storedRecord.status()).isEqualTo(IdempotencyStatus.FAILED);
    assertThat(persistence.failCalls).isOne();
    assertThat(persistence.markUnknownCalls).isZero();
    CompleteRegistrationResult retry = service.complete(command()).block();
    assertThat(retry.status()).isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
    verify(completion, times(1)).complete(any());
    assertThat(persistence.reserveCalls).isOne();
    assertThat(persistence.loadCalls).isOne();
  }

  @Test
  void ambiguousPostDispatchFailureBecomesUnknownAndSameKeyRetryDoesNotResubmit() {
    when(completion.complete(any()))
        .thenReturn(Mono.error(new IllegalStateException("downstream response lost")));

    assertThatThrownBy(() -> service.complete(command()).block())
        .isInstanceOf(IllegalStateException.class);

    assertThat(persistence.storedRecord.status()).isEqualTo(IdempotencyStatus.UNKNOWN_OUTCOME);
    assertThat(persistence.markUnknownCalls).isOne();
    assertThat(persistence.failCalls).isZero();
    CompleteRegistrationResult retry = service.complete(command()).block();
    assertThat(retry.status()).isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
    verify(completion, times(1)).complete(any());
    assertThat(persistence.reserveCalls).isOne();
    assertThat(persistence.loadCalls).isOne();
  }

  private RegistrationController registrationController() {
    var pages = new BffPagesProperties();
    return new RegistrationController(
        null,
        new RegistrationStartWebMapper(Duration.ofMinutes(5)),
        AuthFlowCookieWriter.defaultWriter(),
        service,
        new CompleteRegistrationWebMapper(
            Duration.ofMinutes(5), new RegistrationSuccessPageMapper(pages)),
        null,
        new CancelRegistrationWebMapper(new RegistrationCancellationPageMapper(pages)),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private CapturedHttpResponse completeThroughController(
      RegistrationController controller, String requestId) throws Exception {
    var properties = new RequestLoggingProperties();
    properties.setEnabled(false);
    var sanitizer = new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties()));
    var requestFilter =
        new RequestHeaderContextWebFilter(
            new RequestLoggingWebFilter(properties, sanitizer, objectMapper));
    var request =
        MockServerHttpRequest.post("/api/v1/auth/registration/complete")
            .cookie(new HttpCookie("auth_flow_sid", SID.value()))
            .header("X-XSRF-TOKEN", "csrf-token")
            .header("Idempotency-Key", "reg-complete-same-key")
            .header(RequestCorrelation.REQUEST_ID_HEADER, requestId)
            .header("Accept-Language", "en")
            .header("Origin", "https://portal.example")
            .header("Referer", "https://portal.example/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                objectMapper.writeValueAsString(
                    new CompleteRegistrationRequest("Passw0rd!", "Passw0rd!")));
    var exchange = MockServerWebExchange.from(request);

    requestFilter
        .filter(
            exchange,
            current ->
                DataBufferUtils.join(current.getRequest().getBody())
                    .flatMap(
                        buffer -> {
                          CompleteRegistrationRequest body = readRequest(buffer);
                          return controller
                              .completeRegistration(
                                  body,
                                  current
                                      .getRequest()
                                      .getCookies()
                                      .getFirst("auth_flow_sid")
                                      .getValue(),
                                  current.getRequest().getHeaders().getFirst("X-XSRF-TOKEN"),
                                  current.getRequest().getHeaders().getFirst("Idempotency-Key"),
                                  current
                                      .getRequest()
                                      .getHeaders()
                                      .getFirst(RequestCorrelation.REQUEST_ID_HEADER),
                                  current.getRequest().getHeaders().getFirst("Accept-Language"),
                                  current.getRequest().getHeaders().getFirst("Origin"),
                                  current.getRequest().getHeaders().getFirst("Referer"),
                                  current,
                                  current.getResponse())
                              .flatMap(response -> writeResponse(current, response));
                        }))
        .block();

    DataBuffer responseBuffer = DataBufferUtils.join(exchange.getResponse().getBody()).block();
    byte[] responseBytes = new byte[responseBuffer.readableByteCount()];
    responseBuffer.read(responseBytes);
    DataBufferUtils.release(responseBuffer);
    JsonNode body = objectMapper.readTree(responseBytes);
    return new CapturedHttpResponse(
        exchange.getResponse().getStatusCode().value(),
        body,
        exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER),
        exchange.getResponse().getCookies().getFirst("auth_flow_sid").getValue());
  }

  private CompleteRegistrationRequest readRequest(DataBuffer buffer) {
    byte[] bytes = new byte[buffer.readableByteCount()];
    buffer.read(bytes);
    DataBufferUtils.release(buffer);
    try {
      return objectMapper.readValue(bytes, CompleteRegistrationRequest.class);
    } catch (IOException exception) {
      throw new IllegalStateException("Registration test request could not be decoded", exception);
    }
  }

  private Mono<Void> writeResponse(ServerWebExchange exchange, ResponseEntity<?> response) {
    exchange.getResponse().setStatusCode(response.getStatusCode());
    response
        .getHeaders()
        .forEach((name, values) -> exchange.getResponse().getHeaders().put(name, values));
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(response.getBody());
      return exchange
          .getResponse()
          .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    } catch (JsonProcessingException exception) {
      return Mono.error(exception);
    }
  }

  private record CapturedHttpResponse(
      int statusCode, JsonNode body, String requestId, String authFlowCookie) {}

  private static CompleteRegistrationCommand command() {
    return command("Passw0rd!");
  }

  private static CompleteRegistrationCommand command(String password) {
    return new CompleteRegistrationCommand(
        SID,
        "csrf-token",
        new RegistrationIdempotencyKey("reg-complete-same-key"),
        password,
        password,
        "https://portal.example",
        "https://portal.example/complete",
        "en",
        "req-complete",
        Duration.ofMinutes(5));
  }

  private static AuthFlowSession session() {
    return new AuthFlowSession(
        SID,
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.USERNAME_VERIFIED,
        "csrf-hash",
        null,
        new AuthFlowSessionData("email", "t***@example.com", Map.of()),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private static final class InMemoryIdempotencyPort implements IdempotencyPort {
    private IdempotencyRecord storedRecord;
    private int reserveCalls;
    private int completeCalls;
    private int loadCalls;
    private int failCalls;
    private int markUnknownCalls;

    @Override
    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve command) {
      reserveCalls++;
      if (storedRecord == null) {
        storedRecord =
            new IdempotencyRecord(
                command.operationId(),
                command.fingerprint(),
                command.reservationToken(),
                IdempotencyStatus.PROCESSING,
                null,
                command.now(),
                command.now(),
                command.now().plus(command.ttl()));
        return Mono.just(
            new IdempotencyReservation(
                IdempotencyReservationOutcome.ACQUIRED,
                IdempotencyStatus.PROCESSING,
                null,
                command.reservationToken()));
      }
      if (!storedRecord.fingerprint().equals(command.fingerprint())) {
        return Mono.just(
            new IdempotencyReservation(
                IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                storedRecord.status(),
                null,
                null));
      }
      return Mono.just(
          new IdempotencyReservation(
              IdempotencyReservationOutcome.COMPLETED_REPLAY,
              IdempotencyStatus.COMPLETED,
              storedRecord.terminalResponse(),
              null));
    }

    @Override
    public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete command) {
      completeCalls++;
      storedRecord =
          new IdempotencyRecord(
              command.operationId(),
              command.fingerprint(),
              command.reservationToken(),
              IdempotencyStatus.COMPLETED,
              command.terminalResponse(),
              storedRecord.createdAt(),
              command.now(),
              command.now().plus(command.ttl()));
      return Mono.just(storedRecord);
    }

    @Override
    public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail command) {
      failCalls++;
      storedRecord =
          new IdempotencyRecord(
              command.operationId(),
              command.fingerprint(),
              command.reservationToken(),
              IdempotencyStatus.FAILED,
              null,
              storedRecord.createdAt(),
              command.now(),
              command.now().plus(command.ttl()));
      return Mono.just(storedRecord);
    }

    @Override
    public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown command) {
      markUnknownCalls++;
      storedRecord =
          new IdempotencyRecord(
              command.operationId(),
              command.fingerprint(),
              command.reservationToken(),
              IdempotencyStatus.UNKNOWN_OUTCOME,
              null,
              storedRecord.createdAt(),
              command.now(),
              command.now().plus(command.ttl()));
      return Mono.just(storedRecord);
    }

    @Override
    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operationId) {
      loadCalls++;
      return Mono.just(Optional.ofNullable(storedRecord));
    }
  }
}
