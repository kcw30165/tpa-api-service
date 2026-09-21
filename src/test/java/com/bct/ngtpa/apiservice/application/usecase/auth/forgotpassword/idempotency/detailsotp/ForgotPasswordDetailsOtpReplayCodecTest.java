package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp;

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
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link ForgotPasswordDetailsOtpReplayCodec}. Note: {@code capture()}'s
 * {@code validateCapturable(...)} call throws SYNCHRONOUSLY (not wrapped in {@code Mono.error}),
 * so malformed-result tests use {@code assertThatThrownBy} around the {@code capture(...)} call
 * itself, not {@code StepVerifier}. Dead-code finding (documented, not acted on): {@code
 * IdempotentTerminalResponseSnapshot}'s compact constructor FORCES {@code schemaVersion==1} and
 * {@code mediaType=="application/json"}, so those two sub-conditions inside {@code hydrate()}'s
 * transport-validity check are unreachable via any validly-constructed snapshot; only the {@code
 * safeHeaders}-non-empty sub-condition is tested.
 */
class ForgotPasswordDetailsOtpReplayCodecTest {

  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope =
      mock(ActionTaggedIdempotencyResponseEnvelope.class);
  private final ForgotPasswordDetailsOtpProtectedReplayPort protectedReplay =
      mock(ForgotPasswordDetailsOtpProtectedReplayPort.class);

  private ForgotPasswordDetailsOtpReplayCodec newCodec() {
    return new ForgotPasswordDetailsOtpReplayCodec(envelope, protectedReplay);
  }

  private static ProtectedReplayReference reference() {
    return new ProtectedReplayReference("replay-ref-1234567890abcd");
  }

  private static HydratedIdempotentResponse response(int httpStatus, Map<String, String> headers) {
    return new HydratedIdempotentResponse(httpStatus, MEDIA_TYPE, "body", headers);
  }

  // ----- capture() -------------------------------------------------------------

  @Test
  void captureSuccessWithReferenceSucceeds() {
    ForgotPasswordDetailsOtpResult result =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SUCCESS, "j***@example.com", 300, 60, List.of());
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(result.status());
    when(envelope.encode(any(), any())).thenReturn("{}");
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), reference()))
        .assertNext(
            captured -> {
              assertThat(captured.response().protectedReplayReference()).isEqualTo(reference());
              assertThat(captured.snapshot().protectedReplayReference()).isEqualTo(reference());
            })
        .verifyComplete();
  }

  @Test
  void captureRejectionWithoutReferenceSucceeds() {
    ForgotPasswordDetailsOtpResult result =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED, null, 0, 0, List.of("dateOfBirth"));
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(result.status());
    when(envelope.encode(any(), any())).thenReturn("{}");
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), null))
        .assertNext(captured -> assertThat(captured.snapshot().protectedReplayReference()).isNull())
        .verifyComplete();
  }

  @Test
  void captureReferencePresenceMismatchErrors() {
    ForgotPasswordDetailsOtpResult result =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SUCCESS, "j***@example.com", 300, 60, List.of());
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(result.status());
    StepVerifier.create(newCodec().capture(result, response(status, Map.of()), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void captureTransportMismatchErrors() {
    ForgotPasswordDetailsOtpResult result =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SUCCESS, "j***@example.com", 300, 60, List.of());
    StepVerifier.create(newCodec().capture(result, response(500, Map.of()), reference()))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void captureInvalidSuccessResultThrowsSynchronously() {
    ForgotPasswordDetailsOtpResult malformed =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SUCCESS, "", 300, 60, List.of());
    assertThatThrownBy(() -> newCodec().capture(malformed, response(200, Map.of()), reference()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- hydrate() ---------------------------------------------------------------

  private IdempotentTerminalResponseSnapshot snapshot(
      int httpStatus, Map<String, String> headers, ProtectedReplayReference reference) {
    return new IdempotentTerminalResponseSnapshot(1, httpStatus, MEDIA_TYPE, "{}", headers, reference);
  }

  @Test
  void hydrateNonEmptySafeHeadersErrors() {
    StepVerifier.create(newCodec().hydrate(snapshot(200, Map.of("Retry-After", "30"), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateUnsupportedStatusErrors() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(new ForgotPasswordDetailsOtpReplayCodec.SafePayload("GARBAGE_STATUS", List.of()));
    StepVerifier.create(newCodec().hydrate(snapshot(200, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateHttpStatusMismatchErrors() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordDetailsOtpReplayCodec.SafePayload(
                ForgotPasswordDetailsOtpResult.Status.SUCCESS.name(), List.of()));
    StepVerifier.create(newCodec().hydrate(snapshot(404, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateSuccessMissingProtectedReplayErrors() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordDetailsOtpReplayCodec.SafePayload(
                ForgotPasswordDetailsOtpResult.Status.SUCCESS.name(), List.of()));
    StepVerifier.create(newCodec().hydrate(snapshot(200, Map.of(), null)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateRejectionCarryingProtectedReplayErrors() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordDetailsOtpReplayCodec.SafePayload(
                ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED.name(), List.of()));
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED);
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), reference())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void hydrateRejectionWithoutProtectedReplaySucceeds() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordDetailsOtpReplayCodec.SafePayload(
                ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED.name(), List.of("dateOfBirth")));
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED);
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), null)))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED))
        .verifyComplete();
  }

  @Test
  void hydrateSuccessDelegatesToProtectedReplay() {
    when(envelope.decodePayload(any(), any(), eq(ForgotPasswordDetailsOtpReplayCodec.SafePayload.class)))
        .thenReturn(
            new ForgotPasswordDetailsOtpReplayCodec.SafePayload(
                ForgotPasswordDetailsOtpResult.Status.SUCCESS.name(), List.of()));
    int status = ForgotPasswordDetailsOtpReplayCodec.httpStatus(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    ForgotPasswordDetailsOtpResult delegated =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SUCCESS, "j***@example.com", 200, 45, List.of());
    when(protectedReplay.resolve(any())).thenReturn(Mono.just(delegated));
    StepVerifier.create(newCodec().hydrate(snapshot(status, Map.of(), reference())))
        .expectNext(delegated)
        .verifyComplete();
  }
}
