package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict public-safe terminal snapshot codec for Username Recovery REQUEST. */
public final class UsernameRecoveryRequestReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope;

  public UsernameRecoveryRequestReplayCodec(UsernameRecoveryIdempotencyResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      UsernameRecoveryRequestResult result, HydratedIdempotentResponse response) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    validateTransport(response);
    if (result.outcome() == UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME) {
      return Mono.error(new IllegalArgumentException("REQUEST unknown outcome is not capturable"));
    }
    SafePayload payload = SafePayload.from(result);
    String body = envelope.encode(UsernameRecoveryIdempotencyAction.REQUEST, payload);
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

  public Mono<UsernameRecoveryRequestResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()
        || snapshot.protectedReplayReference() != null) {
      return Mono.error(new IllegalArgumentException("REQUEST replay transport is invalid"));
    }
    final SafePayload payload;
    try {
      payload =
          envelope.decode(
              UsernameRecoveryIdempotencyAction.REQUEST,
              snapshot.serializedSafeBody(),
              SafePayload.class);
      return Mono.just(payload.toResult());
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("REQUEST replay payload is invalid"));
    }
  }

  private static void validateTransport(HydratedIdempotentResponse response) {
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      throw new IllegalArgumentException("REQUEST transient transport is invalid");
    }
  }

  public record SafePayload(
      String outcome,
      String workflowStatus,
      String schemeRef,
      String maskedDestination,
      String destinationType) {
    static SafePayload from(UsernameRecoveryRequestResult result) {
      if (result.outcome() == UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED) {
        return new SafePayload(
            result.outcome().name(),
            result.workflowStatus().name(),
            result.schemeRef().value(),
            result.maskedDestination(),
            result.destinationType().name());
      }
      return new SafePayload(result.outcome().name(), null, null, null, null);
    }

    UsernameRecoveryRequestResult toResult() {
      UsernameRecoveryRequestResult.Outcome decoded =
          UsernameRecoveryRequestResult.Outcome.valueOf(outcome);
      if (decoded == UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME) {
        throw new IllegalArgumentException("REQUEST unknown outcome is not replayable");
      }
      if (decoded != UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED) {
        if (workflowStatus != null
            || schemeRef != null
            || maskedDestination != null
            || destinationType != null) {
          throw new IllegalArgumentException("REQUEST non-accepted payload carries success data");
        }
        return new UsernameRecoveryRequestResult(decoded, null, null, null, null);
      }
      return new UsernameRecoveryRequestResult(
          decoded,
          UsernameRecoveryWorkflowStatus.valueOf(workflowStatus),
          new UsernameRecoverySchemeRef(schemeRef),
          maskedDestination,
          UsernameRecoveryDestinationType.valueOf(destinationType));
    }
  }
}
