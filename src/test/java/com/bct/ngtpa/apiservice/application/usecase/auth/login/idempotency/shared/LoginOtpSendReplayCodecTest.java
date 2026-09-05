package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LoginOtpSendReplayCodecTest {
  private final LoginOtpSendReplayCodec codec = new LoginOtpSendReplayCodec();

  @Test
  void capturesAndHydratesSentWithEmptyHeadersAndNoProtectedReference() {
    var result = new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null);
    var transientResponse = transientResponse(result);

    StepVerifier.create(codec.capture(result, transientResponse))
        .assertNext(
            captured -> {
              assertThat(captured.response()).isEqualTo(transientResponse);
              assertThat(captured.snapshot().safeHeaders()).isEmpty();
              assertThat(captured.snapshot().protectedReplayReference()).isNull();
              assertThat(captured.snapshot().serializedSafeBody())
                  .contains("SENT", "****4567")
                  .doesNotContain("destination-id", "123456", "auth-flow", "csrf");
              StepVerifier.create(codec.hydrate(captured.snapshot()))
                  .expectNext(result)
                  .verifyComplete();
            })
        .verifyComplete();
  }

  @Test
  void roundTripsAllDeterministicStatusesAndTransportCodes() {
    assertRoundTrip(
        new SendLoginOtpResult(SendLoginOtpStatus.AUTH_FLOW_INVALID, null, null, null, null), 400);
    assertRoundTrip(
        new SendLoginOtpResult(SendLoginOtpStatus.INVALID_DESTINATION, null, null, null, null),
        400);
    assertRoundTrip(
        new SendLoginOtpResult(SendLoginOtpStatus.DELIVERY_FAILED, null, null, null, null), 502);
    assertRoundTrip(
        new SendLoginOtpResult(SendLoginOtpStatus.CSRF_INVALID, null, null, null, null), 403);
  }

  @Test
  void rejectsLifecycleUnknownAndUnsupportedTransportMetadata() {
    for (SendLoginOtpStatus status :
        new SendLoginOtpStatus[] {
          SendLoginOtpStatus.COMPLETED_REPLAY,
          SendLoginOtpStatus.DUPLICATE_IN_PROGRESS,
          SendLoginOtpStatus.KEY_REUSED,
          SendLoginOtpStatus.FAILED_EXISTING,
          SendLoginOtpStatus.UNKNOWN_OUTCOME
        }) {
      assertThatThrownBy(
              () -> transientResponse(new SendLoginOtpResult(status, null, null, null, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    var valid =
        codec
            .capture(
                new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null),
                transientResponse(
                    new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null)))
            .block()
            .snapshot();
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    2, 200, valid.mediaType(), valid.serializedSafeBody(), Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported idempotent response snapshot schema");
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    1, 200, "text/plain", valid.serializedSafeBody(), Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotent response media type is unsupported");
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            valid.mediaType(),
            valid.serializedSafeBody(),
            Map.of("Retry-After", "60"),
            null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            valid.mediaType(),
            valid.serializedSafeBody(),
            Map.of(),
            new ProtectedReplayReference("opaque-ref-12345678")));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 401, valid.mediaType(), valid.serializedSafeBody(), Map.of(), null));
  }

  private void assertRoundTrip(SendLoginOtpResult result, int status) {
    assertThat(LoginOtpSendReplayCodec.httpStatus(result.status())).isEqualTo(status);
    StepVerifier.create(
            codec
                .capture(result, transientResponse(result))
                .flatMap(c -> codec.hydrate(c.snapshot())))
        .expectNext(result)
        .verifyComplete();
  }

  private HydratedIdempotentResponse transientResponse(SendLoginOtpResult result) {
    return new HydratedIdempotentResponse(
        LoginOtpSendReplayCodec.httpStatus(result.status()),
        "application/json",
        "{\"transient\":true}",
        Map.of());
  }

  private void reject(IdempotentTerminalResponseSnapshot snapshot) {
    StepVerifier.create(codec.hydrate(snapshot))
        .expectError(IllegalArgumentException.class)
        .verify();
  }
}
