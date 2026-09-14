package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayPayload;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Persists protected Login Start replay continuity without changing an idempotency lifecycle. */
public final class LoginStartProtectedReplayCommitter {
  private final LoginStartReplayPort replayPort;
  private final LoginStartReplayProtectorPort protector;
  private final LoginStartReplayCommitRefGenerator refs;
  private final Clock clock;

  public LoginStartProtectedReplayCommitter(
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      LoginStartReplayCommitRefGenerator refs,
      Clock clock) {
    this.replayPort = Objects.requireNonNull(replayPort);
    this.protector = Objects.requireNonNull(protector);
    this.refs = Objects.requireNonNull(refs);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<StartLoginResult> commit(StartLoginResult result, Duration maximumTtl) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(result, "result must not be null");
          if (result.status() != StartLoginStatus.STARTED) {
            return Mono.error(
                new IllegalArgumentException("Only successful login start can be committed"));
          }
          Duration ttl = boundedTtl(result.expiresInSeconds(), maximumTtl);
          Instant now = clock.instant();
          LoginStartReplayCommitRef ref = refs.generate();
          var payload =
              new LoginStartReplayPayload(
                  result.authFlowSessionId(),
                  result.csrfToken(),
                  result.expiresInSeconds(),
                  result.destinations(),
                  now.plusSeconds(result.expiresInSeconds()));
          var protectedReplay = protector.protect(payload);
          return replayPort.save(ref, protectedReplay, ttl).thenReturn(withReference(result, ref));
        });
  }

  private Duration boundedTtl(Integer sessionSeconds, Duration maximumTtl) {
    if (sessionSeconds == null || sessionSeconds <= 0) {
      throw new IllegalArgumentException("session replay TTL must be positive");
    }
    if (maximumTtl == null || maximumTtl.isZero() || maximumTtl.isNegative()) {
      throw new IllegalArgumentException("maximum replay TTL must be positive");
    }
    Duration sessionTtl = Duration.ofSeconds(sessionSeconds);
    return maximumTtl.compareTo(sessionTtl) <= 0 ? maximumTtl : sessionTtl;
  }

  private StartLoginResult withReference(StartLoginResult result, LoginStartReplayCommitRef ref) {
    return new StartLoginResult(
        result.status(),
        result.authFlowSessionId(),
        result.csrfToken(),
        result.expiresInSeconds(),
        result.destinations(),
        result.failCount(),
        ref.value());
  }
}
