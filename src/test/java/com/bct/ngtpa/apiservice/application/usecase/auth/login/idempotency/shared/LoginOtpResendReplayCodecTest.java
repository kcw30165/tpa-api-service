package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LoginOtpResendReplayCodecTest {
  private final LoginOtpResendReplayCodec codec = new LoginOtpResendReplayCodec();

  @Test
  void roundTripsEveryDeterministicStatusWithAuthoritativeHttpMapping() {
    roundTrip(result(ResendLoginOtpStatus.RESENT, "****4567", 300, 60, null), 200);
    roundTrip(result(ResendLoginOtpStatus.AUTH_FLOW_INVALID, null, null, null, null), 422);
    roundTrip(result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25), 429);
    roundTrip(result(ResendLoginOtpStatus.EXPIRED, "****4567", null, null, null), 422);
    roundTrip(result(ResendLoginOtpStatus.LOCKED, "****4567", null, null, null), 429);
    roundTrip(result(ResendLoginOtpStatus.NOT_REPLACEABLE, "****4567", null, null, null), 422);
    roundTrip(result(ResendLoginOtpStatus.DELIVERY_FAILED, "****4567", null, null, null), 502);
    roundTrip(result(ResendLoginOtpStatus.CSRF_INVALID, null, null, null, null), 403);
  }

  @Test
  void preservesAcquiredResponseAndExactCooldownHeader() {
    var result = result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25);
    var response = transientResponse(result);
    StepVerifier.create(codec.capture(result, response))
        .assertNext(
            captured -> {
              assertThat(captured.response()).isEqualTo(response);
              assertThat(captured.snapshot().safeHeaders()).isEqualTo(Map.of("Retry-After", "25"));
              assertThat(captured.snapshot().protectedReplayReference()).isNull();
              assertThat(captured.snapshot().serializedSafeBody())
                  .doesNotContain("afs_secret", "csrf_secret", "123456", "challenge-id");
            })
        .verifyComplete();
  }

  @Test
  void rejectsLifecycleTechnicalAndUnknownStatuses() {
    for (var status :
        new ResendLoginOtpStatus[] {
          ResendLoginOtpStatus.COMPLETED_REPLAY,
          ResendLoginOtpStatus.DUPLICATE_IN_PROGRESS,
          ResendLoginOtpStatus.KEY_REUSED,
          ResendLoginOtpStatus.FAILED_EXISTING,
          ResendLoginOtpStatus.UNKNOWN_OUTCOME,
          ResendLoginOtpStatus.SYSTEM_FAILED
        }) {
      assertThatThrownBy(() -> LoginOtpResendReplayCodec.httpStatus(status))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsMetadataHeaderReferenceActionAndHttpMismatches() {
    var valid =
        codec
            .capture(
                result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25),
                transientResponse(
                    result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25)))
            .block()
            .snapshot();
    reject(
        new IdempotentTerminalResponseSnapshot(
            2, 429, valid.mediaType(), valid.serializedSafeBody(), valid.safeHeaders(), null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 429, "text/plain", valid.serializedSafeBody(), valid.safeHeaders(), null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 200, valid.mediaType(), valid.serializedSafeBody(), valid.safeHeaders(), null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 429, valid.mediaType(), valid.serializedSafeBody(), Map.of(), null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            429,
            valid.mediaType(),
            valid.serializedSafeBody(),
            Map.of("Retry-After", "24"),
            null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            429,
            valid.mediaType(),
            valid.serializedSafeBody(),
            Map.of("Retry-After", "25", "Location", "/x"),
            null));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1,
            429,
            valid.mediaType(),
            valid.serializedSafeBody(),
            valid.safeHeaders(),
            new ProtectedReplayReference("opaque-ref-12345678")));
    String wrong =
        new LoginSharedResponseEnvelope()
            .encode(
                LoginSharedIdempotencyAction.OTP_SEND,
                new LoginOtpResendReplayCodec.SafePayload(
                    "COOLDOWN_ACTIVE", "****4567", null, 60, 25));
    reject(
        new IdempotentTerminalResponseSnapshot(
            1, 429, valid.mediaType(), wrong, valid.safeHeaders(), null));
  }

  @Test
  void rejectsInvalidSuccessCooldownAndTimingCombinations() {
    assertInvalid(result(ResendLoginOtpStatus.RESENT, null, 300, 60, null));
    assertInvalid(result(ResendLoginOtpStatus.RESENT, "****4567", 0, 60, null));
    assertInvalid(result(ResendLoginOtpStatus.RESENT, "****4567", 300, -1, null));
    assertInvalid(result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, null));
    assertInvalid(result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", 300, 60, 25));
    assertInvalid(result(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 0));
  }

  private void roundTrip(ResendLoginOtpResult result, int status) {
    assertThat(LoginOtpResendReplayCodec.httpStatus(result.status())).isEqualTo(status);
    StepVerifier.create(
            codec
                .capture(result, transientResponse(result))
                .flatMap(c -> codec.hydrate(c.snapshot())))
        .expectNext(result)
        .verifyComplete();
  }

  private void assertInvalid(ResendLoginOtpResult result) {
    assertThatThrownBy(() -> codec.capture(result, transientResponseUnchecked(result)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private HydratedIdempotentResponse transientResponse(ResendLoginOtpResult result) {
    return new HydratedIdempotentResponse(
        LoginOtpResendReplayCodec.httpStatus(result.status()),
        "application/json",
        "{\"transient\":true}",
        LoginOtpResendReplayCodec.safeHeaders(result.retryAfterSeconds()));
  }

  private HydratedIdempotentResponse transientResponseUnchecked(ResendLoginOtpResult result) {
    return new HydratedIdempotentResponse(
        LoginOtpResendReplayCodec.httpStatus(result.status()),
        "application/json",
        "{\"transient\":true}",
        Map.of());
  }

  private ResendLoginOtpResult result(
      ResendLoginOtpStatus status, String masked, Integer expiry, Integer cooldown, Integer retry) {
    return new ResendLoginOtpResult(status, masked, expiry, cooldown, retry);
  }

  private void reject(IdempotentTerminalResponseSnapshot snapshot) {
    StepVerifier.create(codec.hydrate(snapshot))
        .expectError(IllegalArgumentException.class)
        .verify();
  }
}
