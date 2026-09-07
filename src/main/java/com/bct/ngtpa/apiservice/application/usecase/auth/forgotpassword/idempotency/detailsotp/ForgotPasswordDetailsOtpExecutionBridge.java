package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient bridge for DETAILS_AND_OTP shared execution. */
public final class ForgotPasswordDetailsOtpExecutionBridge {
  private final TransientJsonPort json;
  private final ForgotPasswordDetailsOtpReplayCodec codec;

  public ForgotPasswordDetailsOtpExecutionBridge(
      TransientJsonPort json, ForgotPasswordDetailsOtpReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(ForgotPasswordDetailsOtpResult result) {
    return new HydratedIdempotentResponse(
        ForgotPasswordDetailsOtpReplayCodec.httpStatus(result.status()),
        "application/json", json.write(Wire.from(result)), Map.of());
  }

  public ForgotPasswordDetailsOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    if (!"application/json".equalsIgnoreCase(response.mediaType()) || !response.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("DETAILS_AND_OTP transient transport is invalid");
    }
    Wire wire = json.read(response.serializedBody(), Wire.class);
    var result = wire.toResult();
    if (response.httpStatus() != ForgotPasswordDetailsOtpReplayCodec.httpStatus(result.status())) {
      throw new IllegalArgumentException("DETAILS_AND_OTP transient HTTP status mismatch");
    }
    return result;
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, ProtectedReplayReference reference) {
    return codec.capture(fromTransientResponse(response), response, reference);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec.hydrate(snapshot).map(this::toTransientResponse);
  }

  private record Wire(String status, String maskedDestination, int expiresInSeconds,
      int resendAvailableInSeconds, List<String> targets) {
    static Wire from(ForgotPasswordDetailsOtpResult result) {
      return new Wire(result.status().name(), result.maskedDestination(), result.expiresInSeconds(),
          result.resendAvailableInSeconds(), List.copyOf(result.targets()));
    }
    ForgotPasswordDetailsOtpResult toResult() {
      return new ForgotPasswordDetailsOtpResult(ForgotPasswordDetailsOtpResult.Status.valueOf(status),
          maskedDestination, expiresInSeconds, resendAvailableInSeconds,
          targets == null ? List.of() : List.copyOf(targets));
    }
  }
}
