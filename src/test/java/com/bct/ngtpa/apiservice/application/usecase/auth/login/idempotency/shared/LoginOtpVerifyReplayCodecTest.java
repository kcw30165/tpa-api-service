package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpVerifyReplayCodecTest {
  private static final ProtectedReplayReference HANDOFF =
      new ProtectedReplayReference("0123456789abcdef0123456789abcdef");
  private final AtomicInteger recoveries = new AtomicInteger();
  private final LoginOtpVerifyReplayCodec codec =
      new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()),
          (reference, correlationId) -> {
            recoveries.incrementAndGet();
            assertThat(reference).isEqualTo(HANDOFF);
            assertThat(correlationId).isEqualTo("req-1");
            return Mono.just(success("fresh-csrf"));
          });

  @Test
  void capturesSuccessWithoutProtectedValuesAndRecoversThroughOpaqueHandoff() {
    var original = success("original-csrf");
    var transientResponse = transientResponse(original);

    StepVerifier.create(codec.capture(original, transientResponse, HANDOFF))
        .assertNext(
            captured -> {
              assertThat(captured.response())
                  .isEqualTo(transientResponse.withProtectedReplayReference(HANDOFF));
              assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(HANDOFF);
              assertThat(captured.snapshot().safeHeaders()).isEmpty();
              assertThat(captured.snapshot().serializedSafeBody())
                  .contains("ACCOUNTS_READY")
                  .doesNotContain(
                      "original-csrf",
                      "sid_abcdefghijklmnopqrstuvwx",
                      "acc_abcdefghijklmnopqrstuvwx",
                      "00000000012");
              StepVerifier.create(codec.hydrate(captured.snapshot(), "req-1"))
                  .assertNext(
                      replay -> {
                        assertThat(replay.status()).isEqualTo(VerifyLoginOtpStatus.ACCOUNTS_READY);
                        assertThat(replay.authenticatedCsrfToken()).isEqualTo("fresh-csrf");
                        assertThat(replay.authenticatedSessionId())
                            .isEqualTo(original.authenticatedSessionId());
                        assertThat(replay.accounts()).isEqualTo(original.accounts());
                      })
                  .verifyComplete();
            })
        .verifyComplete();
    assertThat(recoveries.get()).isEqualTo(1);
  }

  @Test
  void roundTripsAllDeterministicRejectionsWithoutProtectedRecovery() {
    roundTrip(rejection(VerifyLoginOtpStatus.INVALID, 2, false), 422);
    roundTrip(rejection(VerifyLoginOtpStatus.EXPIRED, null, false), 422);
    roundTrip(rejection(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED, 0, true), 429);
    roundTrip(rejection(VerifyLoginOtpStatus.AUTH_FLOW_INVALID, null, false), 422);
    roundTrip(rejection(VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS, null, true), 422);
    roundTrip(rejection(VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE, null, true), 502);
    roundTrip(rejection(VerifyLoginOtpStatus.SESSION_CREATION_FAILED, null, true), 503);
    roundTrip(rejection(VerifyLoginOtpStatus.CSRF_INVALID, null, false), 403);
    assertThat(recoveries.get()).isZero();
  }

  @Test
  void rejectsUnsupportedStatusesAndInvalidReferencePresence() {
    assertThatThrownBy(
            () ->
                codec.capture(
                    rejection(VerifyLoginOtpStatus.SYSTEM_FAILED, null, false),
                    uncheckedResponse(502),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec.capture(
                    rejection(VerifyLoginOtpStatus.ALREADY_VERIFIED, null, false),
                    uncheckedResponse(422),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> codec.capture(success("csrf"), transientResponse(success("csrf")), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec.capture(
                    rejection(VerifyLoginOtpStatus.EXPIRED, null, false),
                    transientResponse(rejection(VerifyLoginOtpStatus.EXPIRED, null, false)),
                    HANDOFF))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSchemaMediaHeadersActionAndHttpMismatch() {
    var valid =
        codec
            .capture(success("csrf"), transientResponse(success("csrf")), HANDOFF)
            .block()
            .snapshot();
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    2, 200, valid.mediaType(), valid.serializedSafeBody(), Map.of(), HANDOFF))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported idempotent response snapshot schema");
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    1, 200, "text/plain", valid.serializedSafeBody(), Map.of(), HANDOFF))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotent response media type is unsupported");
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            valid.mediaType(),
            valid.serializedSafeBody(),
            Map.of("Retry-After", "1"),
            HANDOFF));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 422, valid.mediaType(), valid.serializedSafeBody(), Map.of(), HANDOFF));
    String wrong =
        new LoginSharedResponseEnvelope(testJson())
            .encode(
                LoginSharedIdempotencyAction.OTP_SEND,
                new LoginOtpVerifyReplayCodec.SafePayload("ACCOUNTS_READY", null, true));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 200, valid.mediaType(), wrong, Map.of(), HANDOFF));
  }

  @Test
  void rejectsUnknownMissingAndInvalidSafePayloadFieldsWithoutRecovery() {
    int before = recoveries.get();
    reject(
        snapshot(
            200,
            "{\"idempotencyAction\":\"otp-verify\",\"payload\":{\"status\":\"ACCOUNTS_READY\",\"remainingVerifyAttempts\":null,\"clearAuthFlowCookie\":true,\"accountRef\":\"unsafe\"}}",
            HANDOFF));
    reject(
        snapshot(
            422,
            "{\"idempotencyAction\":\"otp-verify\",\"payload\":{\"remainingVerifyAttempts\":null,\"clearAuthFlowCookie\":false}}",
            null));
    reject(
        snapshot(
            422,
            "{\"idempotencyAction\":\"otp-verify\",\"payload\":{\"status\":\"NOT_A_STATUS\",\"remainingVerifyAttempts\":null,\"clearAuthFlowCookie\":false}}",
            null));
    reject(snapshot(422, "{\"idempotencyAction\":\"otp-verify\",\"payload\":null}", null));
    assertThat(recoveries.get()).isEqualTo(before);
  }

  @Test
  void rejectsInvalidAttemptAndCookieCombinationsWithoutRecovery() {
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.INVALID, null, false));
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.INVALID, 0, false));
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.INVALID, 1, true));
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED, null, true));
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED, 1, true));
    assertCaptureRejected(rejection(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED, 0, false));
    assertThat(recoveries.get()).isZero();
  }

  @Test
  void failsClosedWhenRequiredProtectedRecoveryIsMissing() {
    var successSnapshot = successSnapshot();

    StepVerifier.create(
            new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()), (reference, correlationId) -> Mono.empty())
                .hydrate(successSnapshot, "req-1"))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Login OTP Verify protected replay recovery is unavailable"))
        .verify();
  }

  @Test
  void failsClosedForInvalidAndFailedProtectedRecoveryResults() {
    var successSnapshot = successSnapshot();

    for (LoginOtpVerifyProtectedReplayResolver resolver :
        List.<LoginOtpVerifyProtectedReplayResolver>of(
            (reference, correlationId) ->
                Mono.just(rejection(VerifyLoginOtpStatus.EXPIRED, null, false)),
            (reference, correlationId) ->
                Mono.just(invalidRecoveredSuccess(List.of(), authenticatedSessionId(), "csrf")),
            (reference, correlationId) ->
                Mono.just(invalidRecoveredSuccess(List.of(account()), null, "csrf")),
            (reference, correlationId) ->
                Mono.just(
                    invalidRecoveredSuccess(List.of(account()), authenticatedSessionId(), " ")),
            (reference, correlationId) ->
                Mono.error(new IllegalStateException("protected detail must not escape")))) {
      StepVerifier.create(new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()), resolver).hydrate(successSnapshot, "req-1"))
          .verifyError();
    }
  }

  private IdempotentTerminalResponseSnapshot successSnapshot() {
    return codec
        .capture(success("csrf"), transientResponse(success("csrf")), HANDOFF)
        .block()
        .snapshot();
  }

  private static VerifyLoginOtpResult invalidRecoveredSuccess(
      List<LoginSessionAccount> accounts,
      AuthenticatedSessionId authenticatedSessionId,
      String authenticatedCsrfToken) {
    VerifyLoginOtpResult result = mock(VerifyLoginOtpResult.class);
    when(result.status()).thenReturn(VerifyLoginOtpStatus.ACCOUNTS_READY);
    when(result.accounts()).thenReturn(accounts);
    when(result.authenticatedSessionId()).thenReturn(authenticatedSessionId);
    when(result.authenticatedCsrfToken()).thenReturn(authenticatedCsrfToken);
    return result;
  }

  private static AuthenticatedSessionId authenticatedSessionId() {
    return new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  }

  private void assertCaptureRejected(VerifyLoginOtpResult result) {
    assertThatThrownBy(
            () ->
                codec.capture(
                    result,
                    uncheckedResponse(LoginOtpVerifyReplayCodec.httpStatus(result.status())),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private IdempotentTerminalResponseSnapshot snapshot(
      int status, String body, ProtectedReplayReference reference) {
    return new IdempotentTerminalResponseSnapshot(
        1, status, "application/json", body, Map.of(), reference);
  }

  private void roundTrip(VerifyLoginOtpResult result, int httpStatus) {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(result.status())).isEqualTo(httpStatus);
    StepVerifier.create(
            codec
                .capture(result, transientResponse(result), null)
                .flatMap(captured -> codec.hydrate(captured.snapshot(), "req-1")))
        .expectNext(result)
        .verifyComplete();
  }

  private void reject(IdempotentTerminalResponseSnapshot snapshot) {
    assertThatThrownBy(() -> codec.hydrate(snapshot, "req-1").block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  private HydratedIdempotentResponse transientResponse(VerifyLoginOtpResult result) {
    return new HydratedIdempotentResponse(
        LoginOtpVerifyReplayCodec.httpStatus(result.status()),
        "application/json",
        "{\"transient\":true}",
        Map.of());
  }

  private HydratedIdempotentResponse uncheckedResponse(int status) {
    return new HydratedIdempotentResponse(status, "application/json", "{}", Map.of());
  }

  private static VerifyLoginOtpResult rejection(
      VerifyLoginOtpStatus status, Integer attempts, boolean clearCookie) {
    return new VerifyLoginOtpResult(status, attempts, clearCookie, List.of(), null, null);
  }

  private static VerifyLoginOtpResult success(String csrf) {
    return new VerifyLoginOtpResult(
        VerifyLoginOtpStatus.ACCOUNTS_READY,
        null,
        true,
        List.of(account()),
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        csrf);
  }

  private static LoginSessionAccount account() {
    return new LoginSessionAccount(
        new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
        "DB",
        "12",
        "00000000012",
        "1",
        "0001",
        "TR",
        "OE",
        null,
        null);
  }

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
