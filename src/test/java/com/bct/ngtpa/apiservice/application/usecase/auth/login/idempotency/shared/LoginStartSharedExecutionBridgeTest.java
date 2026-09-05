package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayPayload;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.ProtectedLoginStartReplay;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartSharedExecutionBridgeTest {
  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

  @Test
  void acquiredResponseStaysTransientAndCompletedReplayRestoresEquivalentTypedResult() {
    var replay = new ReplayPortWithProtectedValue();
    var codec = new LoginStartReplayCodec(replay, protector(), Clock.fixed(NOW, ZoneOffset.UTC));
    var bridge = new LoginStartSharedExecutionBridge(codec);
    var original =
        new StartLoginResult(
            StartLoginStatus.STARTED,
            "afs_secret",
            "csrf_secret",
            120,
            List.of(new LoginDestinationChoice("dst_opaque", "sms", "***1234")),
            0,
            "rpl_opaque_commit_reference");
    var transientResponse = bridge.toTransientResponse(original);

    StepVerifier.create(bridge.capture(transientResponse))
        .assertNext(
            captured -> {
              assertThat(captured.response())
                  .isEqualTo(
                      transientResponse.withProtectedReplayReference(
                          captured.snapshot().protectedReplayReference()));
              assertThat(captured.response().protectedReplayReference())
                  .isEqualTo(captured.snapshot().protectedReplayReference());
              assertThat(captured.snapshot().serializedSafeBody())
                  .doesNotContain("afs_secret", "csrf_secret", "dst_opaque", "***1234");

              StepVerifier.create(bridge.hydrate(captured.snapshot()))
                  .assertNext(
                      hydrated -> {
                        StartLoginResult replayed = bridge.fromTransientResponse(hydrated);
                        assertThat(replayed.status()).isEqualTo(StartLoginStatus.STARTED);
                        assertThat(replayed.authFlowSessionId()).isEqualTo("afs_secret");
                        assertThat(replayed.csrfToken()).isEqualTo("csrf_secret");
                        assertThat(replayed.destinations()).isEqualTo(original.destinations());
                        assertThat(replayed.replayCommitRef())
                            .isEqualTo("rpl_opaque_commit_reference");
                        assertThat(replayed.expiresInSeconds()).isPositive();
                      })
                  .verifyComplete();
            })
        .verifyComplete();

    assertThat(replay.loads).isEqualTo(1);
  }

  @Test
  void rejectsLegacyCompletedReplayAndMalformedTransientBody() {
    var bridge =
        new LoginStartSharedExecutionBridge(
            new LoginStartReplayCodec(
                new ReplayPortWithProtectedValue(), protector(), Clock.systemUTC()));
    assertThatThrownBy(
            () ->
                bridge.toTransientResponse(
                    new StartLoginResult(
                        StartLoginStatus.COMPLETED_REPLAY,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                bridge.fromTransientResponse(
                    new com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse(
                        200, "application/json", "{}", Map.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static LoginStartReplayProtectorPort protector() {
    return new LoginStartReplayProtectorPort() {
      @Override
      public ProtectedLoginStartReplay protect(LoginStartReplayPayload value) {
        throw new AssertionError("capture must not protect again");
      }

      @Override
      public LoginStartReplayPayload unprotect(ProtectedLoginStartReplay value) {
        return new LoginStartReplayPayload(
            "afs_secret",
            "csrf_secret",
            120,
            List.of(new LoginDestinationChoice("dst_opaque", "sms", "***1234")),
            NOW.plusSeconds(120));
      }
    };
  }

  private static final class ReplayPortWithProtectedValue implements LoginStartReplayPort {
    private int loads;

    @Override
    public Mono<Void> save(
        LoginStartReplayCommitRef ref, ProtectedLoginStartReplay replay, Duration ttl) {
      throw new AssertionError("capture must not persist protected replay twice");
    }

    @Override
    public Mono<Optional<ProtectedLoginStartReplay>> load(LoginStartReplayCommitRef ref) {
      loads++;
      return Mono.just(
          Optional.of(new ProtectedLoginStartReplay("v1", "AES/GCM/NoPadding", "iv", "cipher")));
    }

    @Override
    public Mono<Boolean> delete(LoginStartReplayCommitRef ref) {
      throw new AssertionError("hydration must not delete protected replay");
    }
  }
}
