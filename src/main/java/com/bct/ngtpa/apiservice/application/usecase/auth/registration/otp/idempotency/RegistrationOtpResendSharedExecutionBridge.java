package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient and persisted-response bridge for OTP Resend shared execution. */
public final class RegistrationOtpResendSharedExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationOtpResendReplayCodec codec;

  public RegistrationOtpResendSharedExecutionBridge(RegistrationOtpResendReplayCodec codec) {
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(ResendRegistrationOtpResult result) {
    try {
      return new HydratedIdempotentResponse(
          transientStatus(result), MEDIA_TYPE, mapper.writeValueAsString(result), Map.of());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration OTP Resend transient encoding failed");
    }
  }

  public ResendRegistrationOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Registration OTP Resend transient media type is invalid");
    }
    try {
      return mapper.readValue(response.serializedBody(), ResendRegistrationOtpResult.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration OTP Resend transient decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return Mono.fromSupplier(() -> codec.capture(fromTransientResponse(response)));
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return Mono.fromSupplier(() -> toTransientResponse(codec.hydrate(snapshot)));
  }

  private int transientStatus(ResendRegistrationOtpResult result) {
    return switch (result.status()) {
      case RESENT -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID -> 400;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case COOLDOWN_ACTIVE, SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> 429;
      case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "OTP Resend lifecycle replay status is not a transient public result");
    };
  }
}
