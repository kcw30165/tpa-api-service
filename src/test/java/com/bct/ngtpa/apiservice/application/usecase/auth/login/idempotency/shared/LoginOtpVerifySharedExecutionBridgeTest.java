package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpVerifySharedExecutionBridgeTest {
  private static final ProtectedReplayReference HANDOFF =
      new ProtectedReplayReference("0123456789abcdef0123456789abcdef");

  @Test
  void preservesAcquiredResponseAndCompletedReplayRestoresCommittedLogin() {
    var original = success("original-csrf");
    var replayed = success("fresh-csrf");
    var bridge =
        new LoginOtpVerifySharedExecutionBridge(testJson(),
            new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()), (reference, correlationId) -> Mono.just(replayed)));
    var transientResponse = bridge.toTransientResponse(original);
    assertThat(bridge.fromTransientResponse(transientResponse)).isEqualTo(original);

    StepVerifier.create(bridge.capture(transientResponse, HANDOFF))
        .assertNext(
            captured -> {
              assertThat(captured.response())
                  .isEqualTo(transientResponse.withProtectedReplayReference(HANDOFF));
              StepVerifier.create(bridge.hydrate(captured.snapshot(), "req-1"))
                  .assertNext(
                      hydrated -> {
                        var result = bridge.fromTransientResponse(hydrated);
                        assertThat(result).isEqualTo(replayed);
                        assertThat(result.authenticatedCsrfToken())
                            .isNotEqualTo(original.authenticatedCsrfToken());
                      })
                  .verifyComplete();
            })
        .verifyComplete();
  }

  @Test
  void rejectsMalformedUnknownFieldHeadersAndHttpMismatch() {
    var bridge =
        new LoginOtpVerifySharedExecutionBridge(testJson(),
            new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()),
                (reference, correlationId) -> Mono.error(new AssertionError())));
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(200, "application/json", "{}", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    String body = bridge.toTransientResponse(success("csrf")).serializedBody();
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(422, "application/json", body, Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(
                        200, "application/json", body, Map.of("Retry-After", "1"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownTransientFieldAndNeverInvokesRecoveryDuringDecode() {
    java.util.concurrent.atomic.AtomicInteger recoveries =
        new java.util.concurrent.atomic.AtomicInteger();
    var bridge =
        new LoginOtpVerifySharedExecutionBridge(testJson(),
            new LoginOtpVerifyReplayCodec(new LoginSharedResponseEnvelope(testJson()),
                (reference, correlationId) -> {
                  recoveries.incrementAndGet();
                  return Mono.just(success("fresh"));
                }));
    String body = bridge.toTransientResponse(success("csrf")).serializedBody();
    String unknown = body.substring(0, body.length() - 1) + ",\"otp\":\"123456\"}";
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new HydratedIdempotentResponse(200, "application/json", unknown, Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(recoveries.get()).isZero();
  }

  private static VerifyLoginOtpResult success(String csrf) {
    return new VerifyLoginOtpResult(
        VerifyLoginOtpStatus.ACCOUNTS_READY,
        null,
        true,
        List.of(
            new LoginSessionAccount(
                new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB",
                "12",
                "00000000012",
                "1",
                "0001",
                "TR",
                "OE",
                null,
                null)),
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        csrf);
  }

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
