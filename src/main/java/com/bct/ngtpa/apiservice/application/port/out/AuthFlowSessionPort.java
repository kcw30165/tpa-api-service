package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionDeleteResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.InitializeLegacyAuthFlowSessionRevisionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.LegacyAuthFlowSessionRevisionInitializationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionDeleteCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface AuthFlowSessionPort {
  Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command);

  default Mono<AuthFlowSessionSnapshot> createRevisioned(AuthFlowSessionCreateCommand command) {
    return Mono.error(
        new UnsupportedOperationException("Revision-aware create is not implemented"));
  }

  Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId);

  /**
   * Source-compatible bridge for legacy in-memory/test implementations. Production Redis wiring
   * overrides this method and returns persisted revisions.
   */
  default Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(
      AuthFlowSessionId authFlowSessionId) {
    return load(authFlowSessionId)
        .map(
            optional ->
                optional.map(
                    session ->
                        new AuthFlowSessionSnapshot(
                            session,
                            new com.bct.ngtpa.apiservice.application.dto.session.authflow
                                .SessionRevision("00000000000000000000000000000000"))));
  }

  default Mono<LegacyAuthFlowSessionRevisionInitializationResult> initializeLegacyRevision(
      InitializeLegacyAuthFlowSessionRevisionCommand command) {
    return Mono.error(
        new UnsupportedOperationException("Legacy revision initialization is not implemented"));
  }

  default Mono<AuthFlowSessionMutationResult> updateRevisioned(
      RevisionedAuthFlowSessionMutationCommand command) {
    return update(command.updated())
        .map(
            updated ->
                new AuthFlowSessionMutationResult(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                        .AuthFlowSessionMutationOutcome.APPLIED,
                    new AuthFlowSessionSnapshot(
                        updated,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow
                            .SessionRevision("11111111111111111111111111111111"))));
  }

  default Mono<AuthFlowSessionMutationResult> touchRevisioned(
      AuthFlowSessionSnapshot expected, Instant now) {
    return touch(expected.session().authFlowSessionId(), now)
        .map(
            updated ->
                new AuthFlowSessionMutationResult(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                        .AuthFlowSessionMutationOutcome.APPLIED,
                    new AuthFlowSessionSnapshot(
                        updated,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow
                            .SessionRevision("11111111111111111111111111111111"))));
  }

  default Mono<AuthFlowSessionMutationResult> completeRevisioned(
      AuthFlowSessionSnapshot expected, Instant now) {
    return complete(expected.session().authFlowSessionId(), now)
        .map(
            updated ->
                new AuthFlowSessionMutationResult(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                        .AuthFlowSessionMutationOutcome.APPLIED,
                    new AuthFlowSessionSnapshot(
                        updated,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow
                            .SessionRevision("11111111111111111111111111111111"))));
  }

  default Mono<AuthFlowSessionMutationResult> cancelRevisioned(
      AuthFlowSessionSnapshot expected, Instant now) {
    return cancel(expected.session().authFlowSessionId(), now)
        .map(
            updated ->
                new AuthFlowSessionMutationResult(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                        .AuthFlowSessionMutationOutcome.APPLIED,
                    new AuthFlowSessionSnapshot(
                        updated,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow
                            .SessionRevision("11111111111111111111111111111111"))));
  }

  default Mono<AuthFlowSessionDeleteResult> deleteRevisioned(
      RevisionedAuthFlowSessionDeleteCommand command) {
    return delete(command.expected().session().authFlowSessionId())
        .thenReturn(
            new AuthFlowSessionDeleteResult(
                com.bct.ngtpa.apiservice.application.dto.session.authflow
                    .AuthFlowSessionMutationOutcome.APPLIED));
  }

  default Mono<AuthFlowSession> update(AuthFlowSession session) {
    return Mono.error(
        new UnsupportedOperationException("Unversioned auth-flow update is not supported"));
  }

  default Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
    return Mono.error(
        new UnsupportedOperationException("Unversioned auth-flow touch is not supported"));
  }

  default Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
    return Mono.error(
        new UnsupportedOperationException("Unversioned auth-flow complete is not supported"));
  }

  default Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
    return Mono.error(
        new UnsupportedOperationException("Unversioned auth-flow cancel is not supported"));
  }

  default Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
    return Mono.error(
        new UnsupportedOperationException("Unversioned auth-flow delete is not supported"));
  }
}
