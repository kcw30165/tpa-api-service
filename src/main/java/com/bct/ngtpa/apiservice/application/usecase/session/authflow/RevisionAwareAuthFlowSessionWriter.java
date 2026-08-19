package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Central application helper. It has no unversioned persistence fallback. */
public final class RevisionAwareAuthFlowSessionWriter {
  private final AuthFlowSessionPort sessions;

  public RevisionAwareAuthFlowSessionWriter(AuthFlowSessionPort sessions) {
    this.sessions = Objects.requireNonNull(sessions);
  }

  public Mono<AuthFlowSession> updateExpected(AuthFlowSession expected, AuthFlowSession updated) {
    Objects.requireNonNull(expected);
    Objects.requireNonNull(updated);
    return snapshot(expected.authFlowSessionId())
        .flatMap(
            snapshot -> {
              if (!snapshot.session().equals(expected)) return Mono.error(conflict());
              return applied(
                  sessions.updateRevisioned(
                      new RevisionedAuthFlowSessionMutationCommand(snapshot, updated)));
            });
  }

  public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) {
    return snapshot(id).flatMap(s -> applied(sessions.completeRevisioned(s, now)));
  }

  public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
    return snapshot(id).flatMap(s -> applied(sessions.cancelRevisioned(s, now)));
  }

  public Mono<Void> delete(AuthFlowSessionId id) {
    return snapshot(id)
        .flatMap(s -> sessions.deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(s)))
        .flatMap(
            r ->
                r.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                    ? Mono.empty()
                    : Mono.error(conflict()));
  }

  private Mono<AuthFlowSessionSnapshot> snapshot(AuthFlowSessionId id) {
    return sessions
        .loadSnapshot(id)
        .flatMap(
            o ->
                o.map(Mono::just)
                    .orElseGet(
                        () ->
                            Mono.error(
                                new IllegalStateException(
                                    "Auth flow session snapshot not available"))));
  }

  private static Mono<AuthFlowSession> applied(Mono<AuthFlowSessionMutationResult> result) {
    return result.flatMap(
        r ->
            r.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                ? Mono.just(r.snapshot().session())
                : Mono.error(conflict()));
  }

  private static IllegalStateException conflict() {
    return new IllegalStateException("Auth flow session revision conflict");
  }
}
