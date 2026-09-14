package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict public-safe terminal snapshot codec for Username Recovery CANCEL. */
public final class UsernameRecoveryCancelReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope;

  public UsernameRecoveryCancelReplayCodec(UsernameRecoveryIdempotencyResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      UsernameRecoveryCancelResult result, HydratedIdempotentResponse response) {
    Objects.requireNonNull(result);
    validate(response);
    String body =
        envelope.encode(
            UsernameRecoveryIdempotencyAction.CANCEL, new SafePayload(result.outcome().name()));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION,
            response.httpStatus(),
            MEDIA_TYPE,
            body,
            Map.of(),
            null);
    return Mono.just(new IdempotentCapturedResponse(response, snapshot));
  }

  public Mono<UsernameRecoveryCancelResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (!MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()
        || snapshot.protectedReplayReference() != null) {
      return Mono.error(new IllegalArgumentException("CANCEL replay transport is invalid"));
    }
    try {
      SafePayload payload =
          envelope.decode(
              UsernameRecoveryIdempotencyAction.CANCEL,
              snapshot.serializedSafeBody(),
              SafePayload.class);
      return Mono.just(
          new UsernameRecoveryCancelResult(
              UsernameRecoveryCancelResult.Outcome.valueOf(payload.outcome())));
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("CANCEL replay payload is invalid"));
    }
  }

  private static void validate(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      throw new IllegalArgumentException("CANCEL transient transport is invalid");
    }
  }

  public record SafePayload(String outcome) {
    public SafePayload {
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }
}
