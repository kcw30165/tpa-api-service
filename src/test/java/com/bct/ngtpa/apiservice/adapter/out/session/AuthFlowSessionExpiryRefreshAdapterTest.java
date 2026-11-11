package com.bct.ngtpa.apiservice.adapter.out.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionExpiryRefreshAdapterTest {
  @Test
  void refreshSlidesExpiryFromInteractionTime() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var s =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "h",
            null,
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    var revision = new SessionRevision("11111111111111111111111111111111");
    when(port.load(id)).thenReturn(Mono.just(Optional.of(s)));
    when(port.loadSnapshot(id))
        .thenReturn(Mono.just(Optional.of(new AuthFlowSessionSnapshot(s, revision))));
    when(port.updateRevisioned(any()))
        .thenAnswer(
            invocation -> {
              RevisionedAuthFlowSessionMutationCommand command = invocation.getArgument(0);
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          command.updated(),
                          new SessionRevision("22222222222222222222222222222222"))));
            });
    var p = new AuthFlowSessionProperties();
    p.getScenarios().getRegistration().setTtlSeconds(1800);
    StepVerifier.create(new AuthFlowSessionExpiryRefreshAdapter(port, p).refresh(id, now))
        .assertNext(
            x -> {
              assertThat(x.updatedAt()).isEqualTo(now);
              assertThat(x.expiresAt()).isEqualTo(now.plusSeconds(1800));
            })
        .verifyComplete();
  }

  @Test
  void terminalSessionIsNotRefreshed() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var s =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.COMPLETED,
            "h",
            null,
            AuthFlowSessionData.empty(),
            now,
            now,
            now.plusSeconds(5));
    when(port.load(id)).thenReturn(Mono.just(Optional.of(s)));
    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, new AuthFlowSessionProperties())
                .refresh(id, now))
        .expectError(IllegalStateException.class)
        .verify();
    verify(port, never()).loadSnapshot(any());
    verify(port, never()).updateRevisioned(any());
  }

  @Test
  void usernameVerifiedRefreshUsesStrictRevisionCas() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var session =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    var revision = new SessionRevision("11111111111111111111111111111111");
    when(port.load(id)).thenReturn(Mono.just(Optional.of(session)));
    when(port.loadSnapshot(id))
        .thenReturn(Mono.just(Optional.of(new AuthFlowSessionSnapshot(session, revision))));
    when(port.updateRevisionedStrict(any()))
        .thenAnswer(
            invocation -> {
              RevisionedAuthFlowSessionMutationCommand command = invocation.getArgument(0);
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          command.updated(),
                          new SessionRevision("22222222222222222222222222222222"))));
            });
    var properties = new AuthFlowSessionProperties();
    properties.getScenarios().getRegistration().setTtlSeconds(1800);

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, properties)
                .refreshUsernameVerified(id, now))
        .assertNext(refreshed -> assertThat(refreshed.expiresAt()).isEqualTo(now.plusSeconds(1800)))
        .verifyComplete();

    verify(port).updateRevisionedStrict(any());
    verify(port, never()).updateRevisioned(any());
    verify(port, never()).update(any());
  }

  @Test
  void usernameVerifiedRefreshDoesNotWriteWhenSessionIsCompleting() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var session =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.COMPLETING,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    when(port.load(id)).thenReturn(Mono.just(Optional.of(session)));

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, new AuthFlowSessionProperties())
                .refreshUsernameVerified(id, now))
        .expectError(IllegalStateException.class)
        .verify();

    verify(port, never()).loadSnapshot(any());
    verify(port, never()).updateRevisionedStrict(any());
    verify(port, never()).updateRevisioned(any());
  }

  @Test
  void staleUsernameVerifiedRefreshRejectsCompletingRevisionWithoutWrite() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var observed =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    var current =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.COMPLETING,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(2),
            now.plusSeconds(5));
    when(port.load(id)).thenReturn(Mono.just(Optional.of(observed)));
    when(port.loadSnapshot(id))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new AuthFlowSessionSnapshot(
                        current, new SessionRevision("11111111111111111111111111111111")))));

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, new AuthFlowSessionProperties())
                .refreshUsernameVerified(id, now))
        .expectError(IllegalStateException.class)
        .verify();

    verify(port, never()).updateRevisionedStrict(any());
    verify(port, never()).updateRevisioned(any());
    verify(port, never()).update(any());
  }

  @Test
  void revisionCasConflictIsNotRetriedWithAnUnconditionalUpdate() {
    var port = mock(AuthFlowSessionPort.class);
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var session =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    when(port.load(id)).thenReturn(Mono.just(Optional.of(session)));
    when(port.loadSnapshot(id))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new AuthFlowSessionSnapshot(
                        session, new SessionRevision("11111111111111111111111111111111")))));
    when(port.updateRevisionedStrict(any()))
        .thenReturn(
            Mono.just(
                AuthFlowSessionMutationResult.outcome(
                    AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED)));

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, new AuthFlowSessionProperties())
                .refreshUsernameVerified(id, now))
        .expectError(IllegalStateException.class)
        .verify();

    verify(port).updateRevisionedStrict(any());
    verify(port, never()).updateRevisioned(any());
    verify(port, never()).update(any());
  }

  @Test
  void usernameVerifiedRefreshFailsClosedWithoutRevisionCasCapability() {
    var id = new AuthFlowSessionId("afs_safe");
    var now = Instant.parse("2026-07-14T00:00:00Z");
    var session =
        new AuthFlowSession(
            id,
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "h",
            "otp",
            AuthFlowSessionData.empty(),
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.plusSeconds(5));
    var unversionedWrites = new AtomicInteger();
    AuthFlowSessionPort port =
        new AuthFlowSessionPort() {
          @Override
          public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            return Mono.error(new UnsupportedOperationException());
          }

          @Override
          public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId sessionId) {
            return Mono.just(Optional.of(session));
          }

          @Override
          public Mono<AuthFlowSession> update(AuthFlowSession updated) {
            unversionedWrites.incrementAndGet();
            return Mono.just(updated);
          }
        };

    StepVerifier.create(
            new AuthFlowSessionExpiryRefreshAdapter(port, new AuthFlowSessionProperties())
                .refreshUsernameVerified(id, now))
        .expectError(UnsupportedOperationException.class)
        .verify();

    assertThat(unversionedWrites).hasValue(0);
  }
}
