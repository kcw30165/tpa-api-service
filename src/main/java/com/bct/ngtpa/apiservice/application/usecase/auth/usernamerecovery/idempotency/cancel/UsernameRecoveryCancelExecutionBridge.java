package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** In-memory CANCEL transport and strict safe capture boundary. */
public final class UsernameRecoveryCancelExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final UsernameRecoveryCancelReplayCodec codec;

  public UsernameRecoveryCancelExecutionBridge(
      TransientJsonPort json, UsernameRecoveryCancelReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(
      UsernameRecoveryCancelResult result, int httpStatus) {
    return new HydratedIdempotentResponse(
        httpStatus, MEDIA_TYPE, json.write(Objects.requireNonNull(result)), Map.of());
  }

  public UsernameRecoveryCancelResult fromTransientResponse(HydratedIdempotentResponse response) {
    validate(response);
    try {
      return json.read(response.serializedBody(), UsernameRecoveryCancelResult.class);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("CANCEL transient response is invalid");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return codec.capture(fromTransientResponse(response), response);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec
        .hydrate(snapshot)
        .map(result -> toTransientResponse(result, snapshot.httpStatus()));
  }

  private static void validate(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      throw new IllegalArgumentException("CANCEL transient transport is invalid");
    }
  }
}
