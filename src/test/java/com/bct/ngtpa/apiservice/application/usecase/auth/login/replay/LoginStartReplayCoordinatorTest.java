package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginCredentialIdempotencyCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartReplayCoordinatorTest {
  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
  private static final LoginIdempotencyKey KEY = new LoginIdempotencyKey("login_key_opaque");
  private static final LoginCredentialRequestHash HASH =
      new LoginCredentialRequestHash("a".repeat(64));

  @Test
  void commitProtectsThenStoresBeforeMarkingIdempotencyCompleted() {
    List<String> order = new ArrayList<>();
    var storage = new InMemoryReplayPort(order);
    var idempotency = new RecordingIdempotencyPort(order);
    var coordinator = coordinator(storage, idempotency);
    var result =
        new StartLoginResult(
            StartLoginStatus.STARTED,
            "afs_secret",
            "csrf_secret",
            900,
            List.of(new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
            0,
            null);

    StepVerifier.create(coordinator.commit(KEY, HASH, result))
        .assertNext(
            committed -> {
              assertThat(committed.replayCommitRef()).isEqualTo("rpl_opaque_commit_reference");
              assertThat(committed.authFlowSessionId()).isEqualTo("afs_secret");
            })
        .verifyComplete();

    assertThat(order).containsExactly("protect", "save", "complete");
    assertThat(storage.ttl).isEqualTo(Duration.ofSeconds(300));
    assertThat(idempotency.completed.replaySnapshot().opaqueCommitRef())
        .isEqualTo("rpl_opaque_commit_reference");
  }

  @Test
  void completedReplayLoadsProtectedPayloadWithoutNewAuthenticationOrSession() {
    List<String> order = new ArrayList<>();
    var storage = new InMemoryReplayPort(order);
    storage.value = Optional.of(new ProtectedLoginStartReplay("v1", "test", "iv", "cipher"));
    var coordinator = coordinator(storage, new RecordingIdempotencyPort(order));
    var snapshot = new LoginIdempotencyReplaySnapshot("STARTED", "rpl_opaque_commit_reference");

    StepVerifier.create(coordinator.replay(snapshot))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(StartLoginStatus.COMPLETED_REPLAY);
              assertThat(result.authFlowSessionId()).isEqualTo("afs_secret");
              assertThat(result.csrfToken()).isEqualTo("csrf_secret");
              assertThat(result.destinations()).hasSize(1);
            })
        .verifyComplete();
    assertThat(order).containsExactly("load", "unprotect");
  }

  @Test
  void expiredOrMissingReplayBecomesUnknownOutcome() {
    var missing =
        coordinator(
            new InMemoryReplayPort(new ArrayList<>()),
            new RecordingIdempotencyPort(new ArrayList<>()));
    StepVerifier.create(
            missing.replay(
                new LoginIdempotencyReplaySnapshot("STARTED", "rpl_opaque_commit_reference")))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartLoginStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  private LoginStartReplayCoordinator coordinator(
      InMemoryReplayPort storage, RecordingIdempotencyPort idempotency) {
    LoginStartReplayProtectorPort protector =
        new LoginStartReplayProtectorPort() {
          public ProtectedLoginStartReplay protect(LoginStartReplayPayload payload) {
            storage.order.add("protect");
            storage.payload = payload;
            return new ProtectedLoginStartReplay("v1", "test", "iv", "cipher");
          }

          public LoginStartReplayPayload unprotect(ProtectedLoginStartReplay value) {
            storage.order.add("unprotect");
            return storage.payload == null
                ? new LoginStartReplayPayload(
                    "afs_secret",
                    "csrf_secret",
                    900,
                    List.of(
                        new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
                    NOW.plusSeconds(300))
                : storage.payload;
          }
        };
    return new LoginStartReplayCoordinator(
        storage,
        protector,
        () -> new LoginStartReplayCommitRef("rpl_opaque_commit_reference"),
        new LoginCredentialIdempotencyCoordinator(
            idempotency, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(300)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static final class InMemoryReplayPort implements LoginStartReplayPort {
    private final List<String> order;
    private Optional<ProtectedLoginStartReplay> value = Optional.empty();
    private LoginStartReplayPayload payload;
    private Duration ttl;

    InMemoryReplayPort(List<String> order) {
      this.order = order;
    }

    public Mono<Void> save(
        LoginStartReplayCommitRef ref, ProtectedLoginStartReplay replay, Duration ttl) {
      order.add("save");
      this.value = Optional.of(replay);
      this.ttl = ttl;
      return Mono.empty();
    }

    public Mono<Optional<ProtectedLoginStartReplay>> load(LoginStartReplayCommitRef ref) {
      order.add("load");
      return Mono.just(value);
    }

    public Mono<Boolean> delete(LoginStartReplayCommitRef ref) {
      return Mono.just(value.isPresent());
    }
  }

  private static final class RecordingIdempotencyPort implements LoginCredentialIdempotencyPort {
    private final List<String> order;
    private LoginIdempotencyCompleteCommand completed;

    RecordingIdempotencyPort(List<String> order) {
      this.order = order;
    }

    public Mono<LoginIdempotencyStartResult> startProcessing(LoginIdempotencyStartCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<LoginIdempotencyRecord> markCompleted(LoginIdempotencyCompleteCommand c) {
      order.add("complete");
      completed = c;
      return Mono.just(
          new LoginIdempotencyRecord(
              c.action(),
              c.idempotencyKey(),
              c.requestHash(),
              LoginIdempotencyStatus.COMPLETED,
              c.replaySnapshot(),
              NOW,
              NOW,
              NOW.plus(c.ttl())));
    }

    public Mono<LoginIdempotencyRecord> markFailed(LoginIdempotencyFailCommand c) {
      return Mono.just(
          new LoginIdempotencyRecord(
              c.action(),
              c.idempotencyKey(),
              c.requestHash(),
              LoginIdempotencyStatus.FAILED,
              null,
              NOW,
              NOW,
              NOW.plus(c.ttl())));
    }

    public Mono<LoginIdempotencyRecord> markUnknownOutcome(
        LoginIdempotencyUnknownOutcomeCommand c) {
      return Mono.just(
          new LoginIdempotencyRecord(
              c.action(),
              c.idempotencyKey(),
              c.requestHash(),
              LoginIdempotencyStatus.UNKNOWN_OUTCOME,
              null,
              NOW,
              NOW,
              NOW.plus(c.ttl())));
    }

    public Mono<Optional<LoginIdempotencyRecord>> load(
        LoginIdempotencyAction a, LoginIdempotencyKey k) {
      return Mono.just(Optional.empty());
    }

    public Mono<Void> delete(LoginIdempotencyAction a, LoginIdempotencyKey k) {
      return Mono.empty();
    }
  }
}
