package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** In-memory typed START transport and safe capture boundary. */
public final class UsernameRecoveryStartExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final UsernameRecoveryStartReplayCodec codec;

  public UsernameRecoveryStartExecutionBridge(
      TransientJsonPort json, UsernameRecoveryStartReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(
      UsernameRecoveryStartResult result, int httpStatus) {
    Objects.requireNonNull(result);
    return new HydratedIdempotentResponse(httpStatus, MEDIA_TYPE, json.write(result), Map.of());
  }

  public UsernameRecoveryStartResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType()) || !response.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("START transient transport is invalid");
    }
    try {
      return json.read(response.serializedBody(), UsernameRecoveryStartResult.class);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("START transient response is invalid");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, Duration ttl) {
    return codec.capture(fromTransientResponse(response), response, ttl);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec
        .hydrate(snapshot)
        .map(result -> toTransientResponse(result, snapshot.httpStatus()))
        .map(
            response ->
                snapshot.protectedReplayReference() == null
                    ? response
                    : response.withProtectedReplayReference(snapshot.protectedReplayReference()));
  }
}
