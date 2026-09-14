package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisUsernameRecoveryStartProtectedReplayAdapter
    implements UsernameRecoveryStartProtectedReplayPort {
  private static final int MAX_COLLISION_ATTEMPTS = 4;
  private final ReactiveStringRedisTemplate redis;
  private final RedisUsernameRecoveryStartProtectedReplayKeyFactory keys;
  private final RedisUsernameRecoveryStartProtectedReplayJsonMapper json;
  private final UsernameRecoveryStartProtectedReplayProtectorPort protector;
  private final UsernameRecoveryStartProtectedReplayReferenceGenerator references;
  private final AuthFlowSessionPort sessions;
  private final UsernameRecoveryAuthFlowContextBinding contexts;
  private final CreateAuthFlowSessionCsrfUseCase csrf;
  private final Clock clock;

  public RedisUsernameRecoveryStartProtectedReplayAdapter(
      ReactiveStringRedisTemplate redis,
      RedisUsernameRecoveryStartProtectedReplayKeyFactory keys,
      RedisUsernameRecoveryStartProtectedReplayJsonMapper json,
      UsernameRecoveryStartProtectedReplayProtectorPort protector,
      UsernameRecoveryStartProtectedReplayReferenceGenerator references,
      AuthFlowSessionPort sessions,
      UsernameRecoveryAuthFlowContextBinding contexts,
      CreateAuthFlowSessionCsrfUseCase csrf,
      Clock clock) {
    this.redis = Objects.requireNonNull(redis);
    this.keys = Objects.requireNonNull(keys);
    this.json = Objects.requireNonNull(json);
    this.protector = Objects.requireNonNull(protector);
    this.references = Objects.requireNonNull(references);
    this.sessions = Objects.requireNonNull(sessions);
    this.contexts = Objects.requireNonNull(contexts);
    this.csrf = Objects.requireNonNull(csrf);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<ProtectedReplayReference> store(
      UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(state, "state must not be null");
          if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Mono.error(
                new IllegalArgumentException(
                    "Username Recovery START replay TTL must be positive"));
          }
          return storeAttempt(state, ttl, 1);
        });
  }

  private Mono<ProtectedReplayReference> storeAttempt(
      UsernameRecoveryStartProtectedReplayState state, Duration ttl, int attempt) {
    ProtectedReplayReference reference = references.generate();
    String value = json.toJson(protector.protect(reference, state));
    return redis
        .opsForValue()
        .setIfAbsent(keys.keyFor(reference), value, ttl)
        .flatMap(
            saved ->
                Boolean.TRUE.equals(saved)
                    ? Mono.just(reference)
                    : attempt >= MAX_COLLISION_ATTEMPTS
                        ? Mono.error(
                            new IllegalStateException(
                                "Username Recovery START replay reference allocation failed"))
                        : storeAttempt(state, ttl, attempt + 1));
  }

  @Override
  public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference reference) {
    return Mono.defer(
        () ->
            redis
                .opsForValue()
                .get(keys.keyFor(reference))
                .switchIfEmpty(
                    Mono.error(
                        new IllegalArgumentException(
                            "Username Recovery START protected replay is unavailable")))
                .map(json::fromJson)
                .map(value -> protector.unprotect(reference, value))
                .flatMap(this::authorizeAndHydrate));
  }

  private Mono<UsernameRecoveryStartResult> authorizeAndHydrate(
      UsernameRecoveryStartProtectedReplayState state) {
    var now = clock.instant();
    if (!state.expiresAt().isAfter(now)) {
      return Mono.error(
          new IllegalArgumentException("Username Recovery START protected replay is expired"));
    }
    return sessions
        .loadSnapshot(state.authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(
                        snapshot -> {
                          var session = snapshot.session();
                          if (!session.authFlowSessionId().equals(state.authFlowSessionId())
                              || session.scenario() != AuthFlowScenario.USERNAME_RECOVERY
                              || session.status() != AuthFlowStatus.STARTED
                              || session.isExpired(now)
                              || !session.expiresAt().equals(state.expiresAt())) {
                            return Mono.<UsernameRecoveryStartResult>error(
                                new IllegalArgumentException(
                                    "Username Recovery START replay context is inconsistent"));
                          }
                          var context = contexts.load(session);
                          if (context.workflowStatus() != UsernameRecoveryWorkflowStatus.ACTIVE
                              && context.workflowStatus()
                                  != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
                            return Mono.<UsernameRecoveryStartResult>error(
                                new IllegalArgumentException(
                                    "Username Recovery START replay context is inconsistent"));
                          }
                          long remaining = Duration.between(now, session.expiresAt()).toSeconds();
                          if (remaining <= 0) {
                            return Mono.<UsernameRecoveryStartResult>error(
                                new IllegalArgumentException(
                                    "Username Recovery START protected replay is expired"));
                          }
                          int ttl =
                              remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
                          return csrf.createForSession(
                                  new CreateAuthFlowSessionCsrfCommand(
                                      session.authFlowSessionId(), now, ttl, null))
                              .map(
                                  token ->
                                      new UsernameRecoveryStartResult(
                                          UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                                          session.authFlowSessionId(),
                                          token.rawToken(),
                                          token.expiresInSeconds(),
                                          context.candidates().stream()
                                              .map(
                                                  candidate ->
                                                      new UsernameRecoverySchemeProjection(
                                                          candidate.schemeRef(),
                                                              candidate.accountEnv(),
                                                          candidate.trustCode(),
                                                              candidate.registered()))
                                              .toList()));
                        })
                    .orElseGet(
                        () ->
                            Mono.error(
                                new IllegalArgumentException(
                                    "Username Recovery START protected replay is unavailable"))));
  }
}
