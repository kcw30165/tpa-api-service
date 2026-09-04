package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient and persisted-response bridge for OTP Verify shared execution. */
public final class RegistrationOtpVerifySharedExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationOtpVerifyReplayCodec codec;

  public RegistrationOtpVerifySharedExecutionBridge(RegistrationOtpVerifyReplayCodec codec) {
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(VerifyRegistrationOtpResult result) {
    try {
      return new HydratedIdempotentResponse(
          transientStatus(result), MEDIA_TYPE, mapper.writeValueAsString(result), Map.of());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration OTP Verify transient encoding failed");
    }
  }

  public VerifyRegistrationOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Registration OTP Verify transient media type is invalid");
    }
    try {
      return mapper.readValue(response.serializedBody(), VerifyRegistrationOtpResult.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration OTP Verify transient decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return Mono.fromSupplier(() -> codec.capture(fromTransientResponse(response)));
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return Mono.fromSupplier(() -> toTransientResponse(codec.hydrate(snapshot)));
  }

  private int transientStatus(VerifyRegistrationOtpResult result) {
    return switch (result.status()) {
      case VERIFIED -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, OTP_INVALID -> 400;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> 422;
      case TRUST_LOOKUP_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "OTP Verify lifecycle replay status is not a transient public result");
    };
  }
}
