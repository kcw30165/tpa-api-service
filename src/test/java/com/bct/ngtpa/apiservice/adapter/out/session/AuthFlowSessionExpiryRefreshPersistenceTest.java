package com.bct.ngtpa.apiservice.adapter.out.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionExpiryRefreshPersistenceTest {

  @Test
  void successfulInteractionPersistsUpdatedAtAndSlidingExpiresAt() {
    Instant interactionAt = Instant.parse("2026-07-14T10:50:00Z");
    var existing =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_safe"),
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.USERNAME_VERIFIED,
            "csrf-hash",
            "otp-safe",
            AuthFlowSessionData.empty(),
            Instant.parse("2026-07-14T10:43:00Z"),
            Instant.parse("2026-07-14T10:43:49Z"),
            Instant.parse("2026-07-14T11:13:00Z"));
    var sessions = new CapturingSessions(existing);
    var properties = new AuthFlowSessionProperties();
    properties.getScenarios().getRegistration().setTtlSeconds(1800);
    var adapter = new AuthFlowSessionExpiryRefreshAdapter(sessions, properties);

    StepVerifier.create(adapter.refresh(existing.authFlowSessionId(), interactionAt))
        .assertNext(
            refreshed -> {
              assertThat(refreshed.updatedAt()).isEqualTo(interactionAt);
              assertThat(refreshed.expiresAt()).isEqualTo(Instant.parse("2026-07-14T11:20:00Z"));
            })
        .verifyComplete();

    assertThat(sessions.updated).isNotNull();
    assertThat(sessions.updated.updatedAt()).isEqualTo(interactionAt);
    assertThat(sessions.updated.expiresAt()).isEqualTo(Instant.parse("2026-07-14T11:20:00Z"));
  }

  private static final class CapturingSessions implements AuthFlowSessionPort {
    private final AuthFlowSession existing;
    private AuthFlowSession updated;

    private CapturingSessions(AuthFlowSession existing) {
      this.existing = existing;
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      return Mono.just(Optional.of(existing));
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
      updated = session;
      return Mono.just(session);
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId id) {
      return Mono.empty();
    }
  }
}
