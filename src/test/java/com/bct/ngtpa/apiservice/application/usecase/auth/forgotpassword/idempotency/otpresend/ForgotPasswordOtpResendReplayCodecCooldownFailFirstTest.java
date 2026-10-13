package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for the Forgot Password OTP resend cooldown / limit 503.
 *
 * <p>The protected operation returns COOLDOWN_ACTIVE and RESEND_LIMIT_REACHED with no protected
 * replay reference (ForgotPasswordOtpResendProtectedExecutionResult allows a reference only for
 * RESENT), but the replay codec used to demand one for them. Capturing the response therefore
 * failed with a reference mismatch, the shared executor marked the key unknown, the service turned
 * the error into SYSTEM_FAILED and the caller received HTTP 503.
 *
 * <p>Intended behaviour pinned here: both statuses are safe rejections. They are stored without a
 * protected reference and replayed as a status-only result. A rejection that carries a reference is
 * invalid, exactly like SECURITY_REJECTED. EXPIRED still needs a protected reference.
 */
class ForgotPasswordOtpResendReplayCodecCooldownFailFirstTest {

  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope =
      mock(ActionTaggedIdempotencyResponseEnvelope.class);
  private final ForgotPasswordOtpResendProtectedReplayPort protectedReplay =
      mock(ForgotPasswordOtpResendProtectedReplayPort.class);

  private ForgotPasswordOtpResendReplayCodec newCodec() {
    return new ForgotPasswordOtpResendReplayCodec(envelope, protectedReplay);
  }

  private static ProtectedReplayReference reference() {
    return new ProtectedReplayReference("replay-ref-abcdefghijklmn");
  }

  private static HydratedIdempotentResponse response(int httpStatus) {
    return new HydratedIdempotentResponse(httpStatus, MEDIA_TYPE, "body", Map.of());
  }

  private static IdempotentTerminalResponseSnapshot snapshot(
      int httpStatus, ProtectedReplayReference reference) {
    return new IdempotentTerminalResponseSnapshot(
        1, httpStatus, MEDIA_TYPE, "{}", Map.of(), reference);
  }

  /** Same shape the protected operation builds in mapEligibility for a cooldown. */
  private static ResendForgotPasswordOtpResult cooldownActive() {
    return new ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE, "j***@example.com", 240, null, 3, 25);
  }

  /** Same shape the protected operation builds in mapEligibility for the resend limit. */
  private static ResendForgotPasswordOtpResult resendLimitReached() {
    return new ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED, "j***@example.com", 240, null, 0, null);
  }

  private void givenDecodedStatus(ResendForgotPasswordOtpStatus status) {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(new ForgotPasswordOtpResendReplayCodec.SafePayload(status.name()));
  }

  private void assertCapturedWithoutReference(
      ResendForgotPasswordOtpResult result, int expectedHttpStatus) {
    AtomicReference<Object> encoded = new AtomicReference<>();
    when(envelope.encode(any(), any()))
        .thenAnswer(
            invocation -> {
              encoded.set(invocation.getArgument(1));
              return "{}";
            });
    int status = ForgotPasswordOtpResendReplayCodec.httpStatus(result.status());
    assertThat(status).isEqualTo(expectedHttpStatus);

    StepVerifier.create(newCodec().capture(result, response(status), null))
        .assertNext(
            captured -> {
              assertThat(captured.snapshot().protectedReplayReference()).isNull();
              assertThat(captured.snapshot().httpStatus()).isEqualTo(expectedHttpStatus);
              assertThat(captured.response().protectedReplayReference()).isNull();
            })
        .verifyComplete();

    assertThat(encoded.get())
        .isEqualTo(new ForgotPasswordOtpResendReplayCodec.SafePayload(result.status().name()));
  }

  private void assertHydratedStatusOnly(
      ResendForgotPasswordOtpStatus expectedStatus, int httpStatus) {
    givenDecodedStatus(expectedStatus);
    StepVerifier.create(newCodec().hydrate(snapshot(httpStatus, null)))
        .assertNext(
            replayed -> {
              assertThat(replayed.status()).isEqualTo(expectedStatus);
              assertThat(replayed.maskedDestination()).isNull();
              assertThat(replayed.expiresInSeconds()).isNull();
              assertThat(replayed.resendCooldownSeconds()).isNull();
              assertThat(replayed.remainingResendAttempts()).isNull();
              assertThat(replayed.retryAfterSeconds()).isNull();
            })
        .verifyComplete();
  }

  // ----- capture() ----------------------------------------------------------------------

  @Test
  void captureCooldownActiveWithoutReferenceSucceeds() {
    assertCapturedWithoutReference(cooldownActive(), 429);
  }

  @Test
  void captureResendLimitReachedWithoutReferenceSucceeds() {
    assertCapturedWithoutReference(resendLimitReached(), 429);
  }

  @Test
  void captureCooldownActiveCarryingReferenceErrors() {
    ResendForgotPasswordOtpResult result = cooldownActive();
    int status = ForgotPasswordOtpResendReplayCodec.httpStatus(result.status());
    when(envelope.encode(any(), any())).thenReturn("{}");
    StepVerifier.create(newCodec().capture(result, response(status), reference()))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // ----- hydrate() ----------------------------------------------------------------------

  @Test
  void hydrateCooldownActiveWithoutReferenceReturnsStatusOnly() {
    assertHydratedStatusOnly(ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE, 429);
  }

  @Test
  void hydrateResendLimitReachedWithoutReferenceReturnsStatusOnly() {
    assertHydratedStatusOnly(ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED, 429);
  }

  @Test
  void hydrateCooldownActiveCarryingReferenceErrors() {
    givenDecodedStatus(ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE);
    when(protectedReplay.resolve(any())).thenReturn(Mono.just(cooldownActive()));
    StepVerifier.create(newCodec().hydrate(snapshot(429, reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // ----- guard: unchanged behaviour (passes before and after the fix) -------------------

  @Test
  void hydrateExpiredWithoutReferenceStillErrors() {
    givenDecodedStatus(ResendForgotPasswordOtpStatus.EXPIRED);
    StepVerifier.create(newCodec().hydrate(snapshot(422, null)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }
}
