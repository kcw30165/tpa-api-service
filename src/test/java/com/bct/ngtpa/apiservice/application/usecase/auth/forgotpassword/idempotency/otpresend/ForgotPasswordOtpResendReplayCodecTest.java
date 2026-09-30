package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link ForgotPasswordOtpResendReplayCodec} - structurally identical to
 * {@code ForgotPasswordDetailsOtpReplayCodecTest} (same underlying envelope/protected-replay
 * pattern, confirmed round 63). {@code capture()}'s {@code validateCapturable(...)} call throws
 * SYNCHRONOUSLY, so malformed-result tests use {@code assertThatThrownBy}. Dead-code finding
 * (documented, not acted on): the snapshot's schemaVersion/mediaType transport sub-conditions are
 * unreachable (same as the sibling codec) - only safeHeaders-non-empty is tested.
 */
class ForgotPasswordOtpResendReplayCodecTest {

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

  private static HydratedIdempotentResponse response(int httpStatus, Map<String, String> headers) {
    return new HydratedIdempotentResponse(httpStatus, MEDIA_TYPE, "body", headers);
  }

  private static ResendForgotPasswordOtpResult resent() {
    return new ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
  }

  private static ResendForgotPasswordOtpResult securityRejected() {
    return new ResendForgotPasswordOtpResult(
        ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null, null, null, null);
  }

  // ----- capture() -------------------------------------------------------------

  @Test
  void captureRequiringReplayWithReferenceSucceeds() {
    ResendForgotPasswordOtpResult result = resent();
    int status = ForgotPasswordOtpResendReplayCodec.httpStatus(result.status());
    when(envelope.encode(any(), any())).thenReturn("{}");
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), reference()))
        .assertNext(
            captured ->
                assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(reference()))
        .verifyComplete();
  }

  @Test
  void captureNotRequiringReplayWithoutReferenceSucceeds() {
    ResendForgotPasswordOtpResult result = securityRejected();
    int status = ForgotPasswordOtpResendReplayCodec.httpStatus(result.status());
    when(envelope.encode(any(), any())).thenReturn("{}");
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), null))
        .assertNext(captured -> assertThat(captured.snapshot().protectedReplayReference()).isNull())
        .verifyComplete();
  }

  @Test
  void captureReferencePresenceMismatchErrors() {
    ResendForgotPasswordOtpResult result = resent();
    int status = ForgotPasswordOtpResendReplayCodec.httpStatus(result.status());
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void captureTransportMismatchErrors() {
    ResendForgotPasswordOtpResult result = resent();
    StepVerifier.create(newCodec().capture(result, response(404, Map.of()), reference()))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void captureInvalidRejectionResultThrowsSynchronously() {
    ResendForgotPasswordOtpResult malformed =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SECURITY_REJECTED,
            "leaked@example.com",
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> newCodec().capture(malformed, response(403, Map.of()), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- hydrate() ---------------------------------------------------------------

  private IdempotentTerminalResponseSnapshot snapshot(
      int httpStatus, Map<String, String> headers, ProtectedReplayReference reference) {
    return new IdempotentTerminalResponseSnapshot(
        1, httpStatus, MEDIA_TYPE, "{}", headers, reference);
  }

  @Test
  void hydrateNonEmptySafeHeadersErrors() {
    StepVerifier.create(newCodec().hydrate(snapshot(200, Map.of("Retry-After", "30"), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateUnsupportedStatusErrors() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(new ForgotPasswordOtpResendReplayCodec.SafePayload("GARBAGE_STATUS"));
    StepVerifier.create(newCodec().hydrate(snapshot(200, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateHttpStatusMismatchErrors() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordOtpResendReplayCodec.SafePayload(
                ResendForgotPasswordOtpStatus.RESENT.name()));
    StepVerifier.create(newCodec().hydrate(snapshot(404, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateRequiringReplayMissingReferenceErrors() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordOtpResendReplayCodec.SafePayload(
                ResendForgotPasswordOtpStatus.RESENT.name()));
    int status =
        ForgotPasswordOtpResendReplayCodec.httpStatus(ResendForgotPasswordOtpStatus.RESENT);
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), null)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateNotRequiringReplayCarryingReferenceErrors() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordOtpResendReplayCodec.SafePayload(
                ResendForgotPasswordOtpStatus.SECURITY_REJECTED.name()));
    int status =
        ForgotPasswordOtpResendReplayCodec.httpStatus(
            ResendForgotPasswordOtpStatus.SECURITY_REJECTED);
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateNotRequiringReplayWithoutReferenceSucceeds() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordOtpResendReplayCodec.SafePayload(
                ResendForgotPasswordOtpStatus.SECURITY_REJECTED.name()));
    int status =
        ForgotPasswordOtpResendReplayCodec.httpStatus(
            ResendForgotPasswordOtpStatus.SECURITY_REJECTED);
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), null)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SECURITY_REJECTED))
        .verifyComplete();
  }

  @Test
  void hydrateRequiringReplayDelegatesToProtectedReplay() {
    when(envelope.decodePayload(
            any(), any(), eq(ForgotPasswordOtpResendReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordOtpResendReplayCodec.SafePayload(
                ResendForgotPasswordOtpStatus.RESENT.name()));
    int status =
        ForgotPasswordOtpResendReplayCodec.httpStatus(ResendForgotPasswordOtpStatus.RESENT);
    ResendForgotPasswordOtpResult delegated = resent();
    when(protectedReplay.resolve(any())).thenReturn(Mono.just(delegated));
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), reference())))
        .expectNext(delegated)
        .verifyComplete();
  }
}
