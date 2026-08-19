package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthFlowSessionConcurrencyServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
  private static final SessionConcurrencyPolicy SUPPORTED =
      new SessionConcurrencyPolicy(
          SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING);

  private final FakeAuthFlowSessionPort port = new FakeAuthFlowSessionPort();
  private final CapturingSessionIdGenerator idGenerator = new CapturingSessionIdGenerator();
  private final AuthFlowSessionLifecycleService lifecycleService =
      new AuthFlowSessionLifecycleService(
          port, idGenerator, new SessionTtlCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));
  private final AuthFlowSessionConcurrencyService service =
      new AuthFlowSessionConcurrencyService(lifecycleService);

  @Test
  void startCreatesNewSessionWhenNoExistingSession() {
    StepVerifier.create(service.start(command(null, SUPPORTED, "afs")))
        .assertNext(
            result -> {
              assertThat(result.session().authFlowSessionId().value()).startsWith("afs_");
              assertThat(result.decision()).isEqualTo(AuthFlowConcurrencyDecision.CREATED_NEW);
            })
        .verifyComplete();

    assertThat(port.createCount).isEqualTo(1);
    assertThat(port.cancelCount).isZero();
  }

  @Test
  void startUsesSuppliedPrefixNotHardcodedAfs() {
    StepVerifier.create(service.start(command(null, SUPPORTED, "login")))
        .assertNext(
            result -> assertThat(result.session().authFlowSessionId().value()).startsWith("login_"))
        .verifyComplete();
  }

  @Test
  void startReplacesExistingActiveSession() {
    var existingId = new AuthFlowSessionId("afs_existing");
    port.save(session(existingId, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800)));

    StepVerifier.create(service.start(command(existingId, SUPPORTED, "afs")))
        .assertNext(
            result ->
                assertThat(result.decision())
                    .isEqualTo(AuthFlowConcurrencyDecision.REPLACED_EXISTING))
        .verifyComplete();

    assertThat(port.events)
        .containsExactly(
            "load:afs_existing",
            "load:afs_existing",
            "cancel:afs_existing",
            "create:afs_generated_1");
    assertThat(port.sessions.get("afs_existing").status()).isEqualTo(AuthFlowStatus.CANCELLED);
    assertThat(port.createCount).isEqualTo(1);
    assertThat(port.cancelCount).isEqualTo(1);
  }

  @Test
  void startCreatesNewWhenExistingSessionMissing() {
    StepVerifier.create(
            service.start(command(new AuthFlowSessionId("afs_missing"), SUPPORTED, "afs")))
        .assertNext(
            result ->
                assertThat(result.decision())
                    .isEqualTo(AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_MISSING))
        .verifyComplete();

    assertThat(port.createCount).isEqualTo(1);
    assertThat(port.cancelCount).isZero();
  }

  @Test
  void startCreatesNewWhenExistingSessionAlreadyTerminal() {
    for (AuthFlowStatus terminal :
        new AuthFlowStatus[] {
          AuthFlowStatus.COMPLETED, AuthFlowStatus.CANCELLED, AuthFlowStatus.EXPIRED
        }) {
      port.clear();
      idGenerator.reset();
      var existingId = new AuthFlowSessionId("afs_existing");
      port.save(session(existingId, terminal, NOW.plusSeconds(1800)));

      StepVerifier.create(service.start(command(existingId, SUPPORTED, "afs")))
          .assertNext(
              result ->
                  assertThat(result.decision())
                      .isEqualTo(AuthFlowConcurrencyDecision.CREATED_NEW_EXISTING_ALREADY_TERMINAL))
          .verifyComplete();

      assertThat(port.createCount).isEqualTo(1);
      assertThat(port.cancelCount).isZero();
    }
  }

  @Test
  void unsupportedMultiFlowPolicyFailsSafely() {
    var policy =
        new SessionConcurrencyPolicy(
            SessionConcurrencyMode.MULTI_FLOW, SessionOnNewFlowPolicy.REPLACE_EXISTING);
    StepVerifier.create(service.start(command(new AuthFlowSessionId("afs_secret"), policy, "afs")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unsupported auth flow concurrency policy")
                    .hasMessageNotContaining("afs_secret"))
        .verify();
    assertThat(port.createCount).isZero();
  }

  @Test
  void unsupportedAllowParallelPolicyFailsSafely() {
    var policy =
        new SessionConcurrencyPolicy(
            SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionOnNewFlowPolicy.ALLOW_PARALLEL);
    StepVerifier.create(service.start(command(null, policy, "afs")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unsupported auth flow concurrency policy"))
        .verify();
    assertThat(port.createCount).isZero();
  }

  @Test
  void cancelFailurePreventsNewSessionCreation() {
    var existingId = new AuthFlowSessionId("afs_existing_secret");
    port.save(session(existingId, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800)));
    port.failCancel = true;

    StepVerifier.create(service.start(command(existingId, SUPPORTED, "afs")))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Cancel failed")
                    .hasMessageNotContaining("afs_existing_secret"))
        .verify();

    assertThat(port.createCount).isZero();
  }

  private static StartAuthFlowSessionWithConcurrencyCommand command(
      AuthFlowSessionId existingId, SessionConcurrencyPolicy policy, String prefix) {
    return new StartAuthFlowSessionWithConcurrencyCommand(
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.STARTED,
        new SessionTtlPolicy(true, true, 1800),
        prefix,
        16,
        AuthFlowSessionData.empty(),
        null,
        null,
        existingId,
        policy,
        "corr-1");
  }

  private static AuthFlowSession session(
      AuthFlowSessionId id, AuthFlowStatus status, Instant expiresAt) {
    return new AuthFlowSession(
        id,
        AuthFlowScenario.REGISTRATION,
        status,
        null,
        null,
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private static final class CapturingSessionIdGenerator implements SessionIdGenerator {
    private int sequence;

    void reset() {
      sequence = 0;
    }

    @Override
    public SessionId generate(SessionIdGenerationCommand command) {
      sequence++;
      return new SessionId(command.prefix() + "_generated_" + sequence);
    }
  }

  private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
    private final Map<String, AuthFlowSession> sessions = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private int createCount;
    private int cancelCount;
    private boolean failCancel;

    void save(AuthFlowSession session) {
      sessions.put(session.authFlowSessionId().value(), session);
    }

    void clear() {
      sessions.clear();
      events.clear();
      createCount = 0;
      cancelCount = 0;
      failCancel = false;
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      createCount++;
      events.add("create:" + command.authFlowSessionId().value());
      var session =
          new AuthFlowSession(
              command.authFlowSessionId(),
              command.scenario(),
              command.initialStatus(),
              command.csrfTokenHash(),
              command.activeOtpChallengeId(),
              command.data(),
              command.createdAt(),
              command.createdAt(),
              command.expiresAt());
      save(session);
      return Mono.just(session);
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      events.add("load:" + id.value());
      return Mono.just(Optional.ofNullable(sessions.get(id.value())));
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
      save(session);
      return Mono.just(session);
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
      if (failCancel) {
        return Mono.error(new IllegalStateException("Cancel failed"));
      }
      cancelCount++;
      events.add("cancel:" + id.value());
      var existing = sessions.get(id.value());
      var cancelled =
          new AuthFlowSession(
              existing.authFlowSessionId(),
              existing.scenario(),
              AuthFlowStatus.CANCELLED,
              existing.csrfTokenHash(),
              existing.activeOtpChallengeId(),
              existing.data(),
              existing.createdAt(),
              now,
              existing.expiresAt());
      save(cancelled);
      return Mono.just(cancelled);
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId id) {
      sessions.remove(id.value());
      return Mono.empty();
    }
  }
}
