package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.COOKIE_PATH;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.START_PATH;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservation;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationToken;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationTokenGenerator;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.IdempotentStartUsernameRecoveryService;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.LegacyStartReplaySnapshotFixture;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartReplayCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Characterization of an already implemented failure path: a COMPLETED START replay whose persisted
 * snapshot still carries the removed ZERO_REGISTERED outcome must fail closed over HTTP.
 *
 * <p>Real: replay codec, response envelope, execution bridge, {@code DefaultIdempotencyExecutor},
 * {@code IdempotentStartUsernameRecoveryService}, controller, mappers, response policy, cookie
 * writer and the HTTP mapping. Doubled: idempotency storage ({@code IdempotencyPort} and its
 * reservation), the reservation-token source, the idempotency integration policy (operation id and
 * fingerprint derivation), the protected replay storage, and everything below the start operation
 * (CAPTCHA gate, APIM, session store, delivery, destination and template ports).
 *
 * <p>The control test replays a well-formed MEMBER_NOT_FOUND snapshot through the very same chain
 * and expects the existing 422 rejection, which shows the 503 below is not a wiring artefact.
 */
class UsernameRecoveryLegacyStartReplayHttpTest {
  private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");
  private static final ObjectMapper JSON = new ObjectMapper();

  private final UsernameRecoveryBoundaryFixture fixture = new UsernameRecoveryBoundaryFixture();
  private final CountingReplayPort replayPort = new CountingReplayPort();
  private final IdempotencyPort persistence = mock(IdempotencyPort.class);
  private final IdempotencyReservation reservation = mock(IdempotencyReservation.class);
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope =
      new UsernameRecoveryIdempotencyResponseEnvelope(new JacksonTransientJsonAdapter(JSON));
  private final UsernameRecoveryStartReplayCodec codec =
      new UsernameRecoveryStartReplayCodec(envelope, replayPort, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aCompletedLegacyZeroRegisteredReplayFailsClosedAsHttp503WithNoSideEffects()
      throws Exception {
    IdempotentTerminalResponseSnapshot legacy = LegacyStartReplaySnapshotFixture.zeroRegistered(envelope);
    assertThat(legacy.serializedSafeBody()).contains("ZERO_REGISTERED");
    assertThat(legacy.protectedReplayReference()).isNull();

    EntityExchangeResult<byte[]> exchange = post(clientReplaying(legacy));

    String text = new String(exchange.getResponseBodyContent(), StandardCharsets.UTF_8);
    JsonNode body = JSON.readTree(text);
    assertThat(exchange.getStatus().value()).isEqualTo(503);
    assertThat(body.at("/success").asBoolean(true)).isFalse();
    assertThat(body.at("/status").asText()).isEqualTo("SYSTEM_ERROR");
    assertThat(body.at("/errors")).hasSize(1);
    assertThat(body.at("/errors/0/code").asText())
        .isNotBlank()
        .isNotEqualTo("err.usernamerecovery.verification.failed")
        .isNotEqualTo("err.usernamerecovery.registration.required");
    JsonNode data = body.at("/result/data");
    assertThat(data.isMissingNode() || data.isNull()).isTrue();
    assertThat(body.at("/result/nextStep").asText()).isNotEqualTo("SELECT_ACCOUNT");
    assertThat(text)
        .doesNotContain(
            "csrfToken", "schemeRef", "schemes", "ZERO_REGISTERED", "registration.required");

    // no newly issued session cookie
    assertThat(exchange.getResponseCookies()).isEmpty();
    assertThat(exchange.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    // the completed replay was reached once and nothing else touched the idempotency storage
    verify(persistence, times(1)).reserve(any());
    verifyNoMoreInteractions(persistence);
    // no protected replay resolution or storage
    assertThat(replayPort.resolves).isZero();
    assertThat(replayPort.stores).isZero();
    // nothing below the start operation was reached: no CAPTCHA, no APIM lookup, no session, no delivery
    verify(fixture.harness.captcha, never()).consume(any());
    verify(fixture.harness.sessions, never()).createRevisioned(any());
    verify(fixture.harness.sessions, never()).updateRevisioned(any());
    verifyNoInteractions(fixture.apim, fixture.delivery, fixture.destinations, fixture.templates);
    assertThat(fixture.harness.calls).isEmpty();
    assertThat(fixture.harness.creates.get()).isZero();
    assertThat(fixture.harness.updates.get()).isZero();
    assertThat(fixture.harness.stored.get()).isNull();
  }

  @Test
  void aCompletedMemberNotFoundReplayThroughTheSameChainStillReturnsTheExistingRejection()
      throws Exception {
    IdempotentTerminalResponseSnapshot control = LegacyStartReplaySnapshotFixture.memberNotFound(codec);

    EntityExchangeResult<byte[]> exchange = post(clientReplaying(control));

    JsonNode body = JSON.readTree(exchange.getResponseBodyContent());
    assertThat(exchange.getStatus().value()).isEqualTo(422);
    assertThat(body.at("/success").asBoolean(true)).isFalse();
    assertThat(body.at("/status").asText()).isEqualTo("BUSINESS_REJECTED");
    assertThat(body.at("/result/nextStep").asText()).isEqualTo("RESTART_FLOW");
    assertThat(body.at("/errors/0/code").asText())
        .isEqualTo("err.usernamerecovery.verification.failed");
    assertThat(exchange.getResponseCookies()).isEmpty();
    verify(persistence, times(1)).reserve(any());
    verifyNoMoreInteractions(persistence);
    assertThat(replayPort.resolves).isZero();
    assertThat(replayPort.stores).isZero();
    verify(fixture.harness.captcha, never()).consume(any());
    verify(fixture.harness.sessions, never()).createRevisioned(any());
    verifyNoInteractions(fixture.apim, fixture.delivery, fixture.destinations, fixture.templates);
    assertThat(fixture.harness.stored.get()).isNull();
  }

  private WebTestClient clientReplaying(IdempotentTerminalResponseSnapshot snapshot) {
    when(reservation.outcome()).thenReturn(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    when(reservation.terminalResponse()).thenReturn(snapshot);
    when(persistence.reserve(any())).thenReturn(Mono.just(reservation));

    var tokens = mock(IdempotencyReservationTokenGenerator.class);
    when(tokens.generate()).thenReturn(mock(IdempotencyReservationToken.class));
    var executor =
        new DefaultIdempotencyExecutor(persistence, tokens, Clock.fixed(NOW, ZoneOffset.UTC));

    var policy = mock(UsernameRecoveryIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.start(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(policy.possibleSideEffect())
        .thenReturn(error -> IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);

    var bridge =
        new UsernameRecoveryStartExecutionBridge(new JacksonTransientJsonAdapter(JSON), codec);
    var service =
        new IdempotentStartUsernameRecoveryService(
            executor,
            fixture.harness.operation,
            bridge,
            policy,
            new UsernameRecoveryStartValidator(),
            Duration.ofSeconds(600));
    return WebTestClient.bindToController(controllerFor(service)).build();
  }

  private static UsernameRecoveryController controllerFor(StartUsernameRecoveryUseCase start) {
    RequestUsernameRecoveryUseCase request =
        (command, csrf, origin, referer) ->
            Mono.error(new AssertionError("Request use case must not run"));
    CancelUsernameRecoveryUseCase cancel =
        (command, csrf, origin, referer) ->
            Mono.error(new AssertionError("Cancel use case must not run"));
    SessionCookiePolicy authFlow =
        new SessionCookiePolicy(
            "auth_flow_sid", COOKIE_PATH, 600, true, true, SessionCookieSameSite.NONE);
    return new UsernameRecoveryController(
        start,
        new UsernameRecoveryStartWebMapper(),
        new UsernameRecoveryStartResponsePolicy(),
        request,
        new UsernameRecoveryRequestWebMapper(),
        new UsernameRecoveryRequestResponsePolicy(),
        cancel,
        new UsernameRecoveryCancelWebMapper(),
        new UsernameRecoveryCancelResponsePolicy(),
        new UsernameRecoveryCookieWriter(authFlow),
        new HttpSessionCookieAdapter());
  }

  private static EntityExchangeResult<byte[]> post(WebTestClient client) {
    return client
        .post()
        .uri(START_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .header("X-Request-Id", "req-legacy-replay-1")
        .header("Idempotency-Key", "idem-legacy-replay-1")
        .bodyValue(validRequest())
        .exchange()
        .expectBody()
        .returnResult();
  }

  /** Protected replay storage: any use is a test failure, and every call is counted. */
  private static final class CountingReplayPort implements UsernameRecoveryStartProtectedReplayPort {
    private int stores;
    private int resolves;

    @Override
    public Mono<ProtectedReplayReference> store(
        UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
      stores++;
      return Mono.error(new AssertionError("a session-less snapshot must not use protected storage"));
    }

    @Override
    public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference reference) {
      resolves++;
      return Mono.error(new AssertionError("a session-less snapshot must not resolve protected state"));
    }
  }
}
