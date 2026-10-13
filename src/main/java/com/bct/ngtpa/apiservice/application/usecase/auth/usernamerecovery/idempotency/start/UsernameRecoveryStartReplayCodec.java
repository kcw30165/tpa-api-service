package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult.Outcome;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict action-tagged safe snapshot codec for Username Recovery START. */
public final class UsernameRecoveryStartReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope;
  private final UsernameRecoveryStartProtectedReplayPort protectedReplay;
  private final Clock clock;

  public UsernameRecoveryStartReplayCodec(
      UsernameRecoveryIdempotencyResponseEnvelope envelope,
      UsernameRecoveryStartProtectedReplayPort protectedReplay,
      Clock clock) {
    this.envelope = Objects.requireNonNull(envelope);
    this.protectedReplay = Objects.requireNonNull(protectedReplay);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<IdempotentCapturedResponse> capture(
      UsernameRecoveryStartResult result, HydratedIdempotentResponse response, Duration ttl) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    positive(ttl);
    validateTransport(response);
    SafePayload payload = SafePayload.from(result);
    String body = envelope.encode(UsernameRecoveryIdempotencyAction.START, payload);
    if (result.outcome() != Outcome.SESSION_CREATED) {
      return Mono.just(captured(response, body, null));
    }
    if (result.authFlowSessionId() == null) {
      return Mono.error(new IllegalArgumentException("START session continuity is incomplete"));
    }
    return protectedReplay
        .store(
            new UsernameRecoveryStartProtectedReplayState(
                result.authFlowSessionId(), clock.instant().plus(ttl)),
            ttl)
        .switchIfEmpty(
            Mono.error(new IllegalArgumentException("START protected replay is unavailable")))
        .map(reference -> captured(response, body, reference));
  }

  public Mono<UsernameRecoveryStartResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()) {
      return Mono.error(new IllegalArgumentException("START replay transport is invalid"));
    }
    final SafePayload payload;
    try {
      payload =
          envelope.decode(
              UsernameRecoveryIdempotencyAction.START,
              snapshot.serializedSafeBody(),
              SafePayload.class);
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("START replay payload is invalid"));
    }
    final Outcome outcome;
    try {
      outcome = Outcome.valueOf(payload.outcome());
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("START replay outcome is unsupported"));
    }
    if (outcome == Outcome.SESSION_CREATED) {
      if (snapshot.protectedReplayReference() == null || payload.schemes() != null) {
        return Mono.error(
            new IllegalArgumentException("START protected replay reference mismatch"));
      }
      return protectedReplay
          .resolve(snapshot.protectedReplayReference())
          .switchIfEmpty(
              Mono.error(new IllegalArgumentException("START protected replay is unavailable")))
          .flatMap(this::validateResolved);
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("START non-session replay cannot be protected"));
    }
    try {
      return Mono.just(payload.toNonSessionResult(outcome));
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("START replay payload is invalid"));
    }
  }

  private Mono<UsernameRecoveryStartResult> validateResolved(UsernameRecoveryStartResult result) {
    if (result == null || result.outcome() != Outcome.SESSION_CREATED) {
      return Mono.error(new IllegalArgumentException("START protected replay result is invalid"));
    }
    return Mono.just(result);
  }

  private IdempotentCapturedResponse captured(
      HydratedIdempotentResponse response,
      String safeBody,
      com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference reference) {
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            IdempotentTerminalResponseSnapshot.CURRENT_SCHEMA_VERSION,
            response.httpStatus(),
            MEDIA_TYPE,
            safeBody,
            Map.of(),
            reference);
    var hydrated = reference == null ? response : response.withProtectedReplayReference(reference);
    return new IdempotentCapturedResponse(hydrated, snapshot);
  }

  private static void validateTransport(HydratedIdempotentResponse response) {
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      throw new IllegalArgumentException("START transient transport is invalid");
    }
  }

  private static void positive(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("START replay TTL must be positive");
    }
  }

  public record SafePayload(String outcome, List<SchemeWire> schemes) {
    static SafePayload from(UsernameRecoveryStartResult result) {
      return switch (result.outcome()) {
        case SESSION_CREATED -> new SafePayload(result.outcome().name(), null);
        case MEMBER_NOT_FOUND -> new SafePayload(result.outcome().name(), List.of());
        case ZERO_REGISTERED ->
            new SafePayload(
                result.outcome().name(),
                result.schemes().stream()
                    .map(s -> new SchemeWire(s.accountEnv(), s.trustCode(), s.registered()))
                    .toList());
      };
    }

    UsernameRecoveryStartResult toNonSessionResult(Outcome decoded) {
      if (decoded == Outcome.MEMBER_NOT_FOUND) {
        if (schemes == null || !schemes.isEmpty()) throw new IllegalArgumentException();
        return UsernameRecoveryStartResult.withoutSession(decoded, List.of());
      }
      if (decoded != Outcome.ZERO_REGISTERED || schemes == null || schemes.isEmpty()) {
        throw new IllegalArgumentException();
      }
      List<UsernameRecoverySchemeProjection> projections =
          schemes.stream()
              .map(
                  s -> {
                    if (s.accountEnv() == null || s.trustCode() == null || s.registered()) {
                      throw new IllegalArgumentException();
                    }
                    return new UsernameRecoverySchemeProjection(
                        null, s.accountEnv(), s.trustCode(), false);
                  })
              .toList();
      return UsernameRecoveryStartResult.withoutSession(decoded, projections);
    }
  }

  public record SchemeWire(String accountEnv, String trustCode, boolean registered) {}
}
