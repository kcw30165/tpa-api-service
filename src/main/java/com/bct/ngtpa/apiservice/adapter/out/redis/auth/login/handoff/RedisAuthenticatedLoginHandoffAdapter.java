package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.handoff;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisAuthenticatedLoginHandoffAdapter implements AuthenticatedLoginHandoffPort {
  private final RedisLoginIdempotencyKeyFactory keys;
  private final LoginIdempotencyPersistenceIdDeriver ids;
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper mapper;

  public RedisAuthenticatedLoginHandoffAdapter(
      RedisLoginIdempotencyKeyFactory keys,
      LoginIdempotencyPersistenceIdDeriver ids,
      ReactiveStringRedisTemplate redis,
      ObjectMapper mapper) {
    this.keys = Objects.requireNonNull(keys);
    this.ids = Objects.requireNonNull(ids);
    this.redis = Objects.requireNonNull(redis);
    this.mapper = Objects.requireNonNull(mapper).copy();
  }

  public Mono<AuthenticatedLoginHandoffCreateResult> createIfAbsent(
      CreateAuthenticatedLoginHandoffCommand c) {
    var p = c.proof();
    var h =
        new AuthenticatedLoginHandoff(
            p.handoffReference(),
            p.operationReference(),
            p.reservationHash(),
            AuthenticatedLoginHandoffStatus.RESERVED,
            c.proposedAuthenticatedSessionId(),
            null,
            p.committedAt(),
            c.expiresAt());
    Duration ttl = Duration.between(p.committedAt(), c.expiresAt());
    return redis
        .opsForValue()
        .setIfAbsent(key(p.handoffReference()), json(h), ttl)
        .flatMap(
            created ->
                created
                    ? Mono.just(
                        new AuthenticatedLoginHandoffCreateResult(
                            AuthenticatedLoginHandoffCreateOutcome.CREATED, h))
                    : loadByHandoff(p.handoffReference())
                        .map(
                            existing ->
                                new AuthenticatedLoginHandoffCreateResult(
                                    sameIdentity(existing, p)
                                        ? AuthenticatedLoginHandoffCreateOutcome.EXISTING
                                        : AuthenticatedLoginHandoffCreateOutcome.CONFLICT,
                                    sameIdentity(existing, p) ? existing : null))
                        .onErrorReturn(
                            new AuthenticatedLoginHandoffCreateResult(
                                AuthenticatedLoginHandoffCreateOutcome.CONFLICT, null)));
  }

  public Mono<AuthenticatedLoginHandoff> loadByHandoff(AuthenticatedLoginHandoffReference r) {
    return redis
        .opsForValue()
        .get(key(r))
        .switchIfEmpty(Mono.error(new IllegalStateException("handoff missing")))
        .map(this::fromJson);
  }

  public Mono<AuthenticatedLoginHandoff> recordAuthenticatedSessionCommitted(
      RecordAuthenticatedSessionCommittedCommand c) {
    return mutate(
        c.handoffReference(),
        old -> {
          requireIdentity(old, c.operationReference(), c.reservationHash());
          if (old.authenticatedSessionId() != null
              && !old.authenticatedSessionId().equals(c.authenticatedSessionId()))
            throw new IllegalStateException("handoff session conflict");
          return new AuthenticatedLoginHandoff(
              old.handoffReference(),
              old.operationReference(),
              old.reservationHash(),
              AuthenticatedLoginHandoffStatus.AUTHENTICATED_SESSION_COMMITTED,
              c.authenticatedSessionId(),
              old.cleanupProof(),
              old.createdAt(),
              old.expiresAt());
        });
  }

  public Mono<AuthenticatedLoginHandoff> recordCleanupProof(RecordAuthFlowCleanupProofCommand c) {
    return mutate(
        c.handoffReference(),
        old -> {
          if (old.authenticatedSessionId() == null
              || !old.authenticatedSessionId().equals(c.authenticatedSessionId()))
            throw new IllegalStateException("cleanup session conflict");
          if (old.cleanupProof() != null && !old.cleanupProof().equals(c.cleanupProof()))
            throw new IllegalStateException("cleanup proof conflict");
          return new AuthenticatedLoginHandoff(
              old.handoffReference(),
              old.operationReference(),
              old.reservationHash(),
              AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED,
              old.authenticatedSessionId(),
              c.cleanupProof(),
              old.createdAt(),
              old.expiresAt());
        });
  }

  private Mono<AuthenticatedLoginHandoff> mutate(
      AuthenticatedLoginHandoffReference r,
      java.util.function.Function<AuthenticatedLoginHandoff, AuthenticatedLoginHandoff> fn) {
    String key = key(r);
    return redis
        .opsForValue()
        .get(key)
        .switchIfEmpty(Mono.error(new IllegalStateException("handoff missing")))
        .map(this::fromJson)
        .flatMap(
            old -> {
              var next = fn.apply(old);
              Duration ttl = Duration.between(Instant.now(), old.expiresAt());
              if (ttl.isZero() || ttl.isNegative())
                return Mono.error(new IllegalStateException("handoff expired"));
              return redis.opsForValue().set(key, json(next), ttl).thenReturn(next);
            });
  }

  private String key(AuthenticatedLoginHandoffReference r) {
    var publicIdentity = new LoginIdempotencyKey(r.value());
    return keys.keyFor(
            LoginIdempotencyAction.OTP_VERIFY,
            ids.derive(LoginIdempotencyAction.OTP_VERIFY, publicIdentity))
        + ":handoff";
  }

  private boolean sameIdentity(AuthenticatedLoginHandoff h, LoginOtpConsumptionProof p) {
    return h.reservationHash().equals(p.reservationHash())
        && h.operationReference().equals(p.operationReference())
        && h.handoffReference().equals(p.handoffReference());
  }

  private void requireIdentity(
      AuthenticatedLoginHandoff h, LoginOtpVerificationOperationReference o, String hash) {
    if (!h.operationReference().equals(o) || !h.reservationHash().equals(hash))
      throw new IllegalStateException("handoff identity conflict");
  }

  private String json(AuthenticatedLoginHandoff h) {
    try {
      return mapper.writeValueAsString(h);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize authenticated Login handoff");
    }
  }

  private AuthenticatedLoginHandoff fromJson(String s) {
    try {
      return mapper.readValue(s, AuthenticatedLoginHandoff.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to deserialize authenticated Login handoff");
    }
  }
}
