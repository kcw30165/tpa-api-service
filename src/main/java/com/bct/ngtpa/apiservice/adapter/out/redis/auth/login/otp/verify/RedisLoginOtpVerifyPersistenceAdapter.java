package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisLoginOtpVerifyPersistenceAdapter implements LoginOtpVerifyPersistencePort {
  private final OtpChallengeStatePort challenges;
  private final OtpCodeHasher hasher;
  private final RedisLoginIdempotencyKeyFactory keys;
  private final LoginIdempotencyPersistenceIdDeriver ids;
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public RedisLoginOtpVerifyPersistenceAdapter(
      OtpChallengeStatePort c,
      OtpCodeHasher h,
      RedisLoginIdempotencyKeyFactory k,
      LoginIdempotencyPersistenceIdDeriver i,
      ReactiveStringRedisTemplate r) {
    challenges = Objects.requireNonNull(c);
    hasher = Objects.requireNonNull(h);
    keys = Objects.requireNonNull(k);
    ids = Objects.requireNonNull(i);
    redis = Objects.requireNonNull(r);
  }

  public Mono<LoginOtpVerifyReservationResult> reserveOrLoad(ReserveLoginOtpVerifyCommand c) {
    var r =
        new LoginOtpVerifyReservation(
            c.requestHash(),
            LoginOtpVerifyReservationStatus.PROCESSING,
            c.proposedHandoff(),
            c.proposedOperation(),
            null,
            null,
            null,
            c.now(),
            c.expiresAt());
    Duration ttl = Duration.between(c.now(), c.expiresAt());
    String key = key(c.publicKey());
    return redis
        .opsForValue()
        .setIfAbsent(key, json(r), ttl)
        .flatMap(
            created ->
                created
                    ? Mono.just(
                        new LoginOtpVerifyReservationResult(
                            LoginOtpVerifyReservationOutcome.STARTED, r))
                    : loadReservation(c.publicKey(), c.requestHash()));
  }

  public Mono<LoginOtpVerifyReservationResult> loadReservation(LoginIdempotencyKey k, String hash) {
    return redis
        .opsForValue()
        .get(key(k))
        .map(this::fromJson)
        .map(
            r ->
                new LoginOtpVerifyReservationResult(
                    r.requestHash().equals(hash)
                        ? LoginOtpVerifyReservationOutcome.EXISTING
                        : LoginOtpVerifyReservationOutcome.REQUEST_HASH_MISMATCH,
                    r))
        .defaultIfEmpty(
            new LoginOtpVerifyReservationResult(
                LoginOtpVerifyReservationOutcome.CORRUPT_STATE, null));
  }

  public Mono<LoginOtpConsumptionProof> loadConsumptionProof(LoginIdempotencyKey k, String h) {
    return loadReservation(k, h)
        .flatMap(
            r ->
                r.outcome() == LoginOtpVerifyReservationOutcome.EXISTING
                        && r.reservation().proof() != null
                    ? Mono.just(r.reservation().proof())
                    : Mono.empty());
  }

  public Mono<LoginOtpVerifyReservation> recordVerificationCommitted(
      RecordLoginOtpVerifyCommittedCommand c) {
    return mutate(
        c.publicKey(),
        c.requestHash(),
        old ->
            new LoginOtpVerifyReservation(
                old.requestHash(),
                LoginOtpVerifyReservationStatus.VERIFICATION_COMMITTED,
                old.handoffReference(),
                old.operationReference(),
                c.proof(),
                c.outcome(),
                c.remainingAttempts(),
                old.createdAt(),
                old.expiresAt()));
  }

  public Mono<LoginOtpVerifyReservation> recordConclusiveFailure(
      LoginIdempotencyKey k, String h, LoginOtpVerifyPersistenceOutcome o, Integer rem) {
    return mutate(
        k,
        h,
        old ->
            new LoginOtpVerifyReservation(
                old.requestHash(),
                LoginOtpVerifyReservationStatus.FAILED,
                old.handoffReference(),
                old.operationReference(),
                old.proof(),
                o,
                rem,
                old.createdAt(),
                old.expiresAt()));
  }

  public Mono<LoginOtpVerifyReservation> recordUnknownOutcome(LoginIdempotencyKey k, String h) {
    return mutate(
        k,
        h,
        old ->
            new LoginOtpVerifyReservation(
                old.requestHash(),
                LoginOtpVerifyReservationStatus.UNKNOWN_OUTCOME,
                old.handoffReference(),
                old.operationReference(),
                old.proof(),
                old.attemptOutcome(),
                old.remainingAttempts(),
                old.createdAt(),
                old.expiresAt()));
  }

  public Mono<LoginOtpVerifyReservation> complete(LoginIdempotencyKey k, String h) {
    return mutate(
        k,
        h,
        old -> {
          if (old.proof() == null)
            throw new IllegalStateException("OTP verification proof missing");
          return new LoginOtpVerifyReservation(
              old.requestHash(),
              LoginOtpVerifyReservationStatus.COMPLETED,
              old.handoffReference(),
              old.operationReference(),
              old.proof(),
              old.attemptOutcome(),
              old.remainingAttempts(),
              old.createdAt(),
              old.expiresAt());
        });
  }

  public Mono<LoginOtpVerifyPersistenceResult> verify(RevisionedLoginOtpVerifyCommand c) {
    return challenges
        .findById(c.expectedChallengeId())
        .flatMap(
            found -> {
              if (found.isEmpty())
                return Mono.just(
                    out(LoginOtpVerifyPersistenceOutcome.CHALLENGE_NOT_FOUND, 0, null));
              OtpChallenge x = found.orElseThrow();
              if (x.status() == OtpStatus.VERIFIED || x.status() == OtpStatus.CONSUMED)
                return Mono.just(
                    out(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED, 0, null));
              if (x.status() == OtpStatus.LOCKED)
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.LOCKED, 0, null));
              if (x.status() == OtpStatus.EXPIRED || !x.expiresAt().isAfter(c.verificationTime()))
                return Mono.just(out(LoginOtpVerifyPersistenceOutcome.EXPIRED, 0, null));
              if (x.status() != OtpStatus.CREATED)
                return Mono.just(
                    out(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED, 0, null));
              boolean match = hasher.matches(c.submittedOtp().revealForComparison(), x.codeHash());
              int failures = x.failedAttemptCount() + (match ? 0 : 1);
              boolean locked = failures >= x.maxVerifyAttempts();
              OtpStatus status =
                  match ? OtpStatus.CONSUMED : (locked ? OtpStatus.LOCKED : OtpStatus.CREATED);
              OtpChallenge updated =
                  new OtpChallenge(
                      x.otpChallengeId(),
                      x.scenario(),
                      x.workflowRef(),
                      x.channel(),
                      x.destinationHash(),
                      x.codeHash(),
                      status,
                      failures,
                      x.maxVerifyAttempts(),
                      x.resendAttemptCount(),
                      x.maxResendAttempts(),
                      x.createdAt(),
                      x.expiresAt(),
                      x.lastSentAt(),
                      x.invalidatedAt(),
                      match ? c.verificationTime() : x.verifiedAt());
              LoginOtpConsumptionProof proof =
                  match
                      ? new LoginOtpConsumptionProof(
                          c.reservationHash(),
                          c.handoffReference(),
                          c.operationReference(),
                          "committed",
                          c.verificationTime())
                      : null;
              return challenges
                  .update(updated)
                  .thenReturn(
                      out(
                          match
                              ? LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED
                              : (locked
                                  ? LoginOtpVerifyPersistenceOutcome.LOCKED
                                  : LoginOtpVerifyPersistenceOutcome.INVALID),
                          Math.max(0, x.maxVerifyAttempts() - failures),
                          proof));
            })
        .onErrorReturn(out(LoginOtpVerifyPersistenceOutcome.SYSTEM_FAILURE, null, null));
  }

  private Mono<LoginOtpVerifyReservation> mutate(
      LoginIdempotencyKey k,
      String hash,
      java.util.function.Function<LoginOtpVerifyReservation, LoginOtpVerifyReservation> fn) {
    String key = key(k);
    return redis
        .opsForValue()
        .get(key)
        .switchIfEmpty(
            Mono.error(new IllegalStateException("OTP verification reservation missing")))
        .map(this::fromJson)
        .flatMap(
            old -> {
              if (!old.requestHash().equals(hash))
                return Mono.error(new IllegalStateException("OTP verification request mismatch"));
              var n = fn.apply(old);
              Duration ttl = Duration.between(Instant.now(), old.expiresAt());
              if (ttl.isZero() || ttl.isNegative())
                return Mono.error(
                    new IllegalStateException("OTP verification reservation expired"));
              return redis.opsForValue().set(key, json(n), ttl).thenReturn(n);
            });
  }

  private String key(LoginIdempotencyKey k) {
    return keys.keyFor(
        LoginIdempotencyAction.OTP_VERIFY, ids.derive(LoginIdempotencyAction.OTP_VERIFY, k));
  }

  private String json(LoginOtpVerifyReservation r) {
    try {
      return mapper.writeValueAsString(r);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize OTP verification reservation");
    }
  }

  private LoginOtpVerifyReservation fromJson(String v) {
    try {
      return mapper.readValue(v, LoginOtpVerifyReservation.class);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to deserialize OTP verification reservation");
    }
  }

  private LoginOtpVerifyPersistenceResult out(
      LoginOtpVerifyPersistenceOutcome o, Integer r, LoginOtpConsumptionProof p) {
    return new LoginOtpVerifyPersistenceResult(o, r, p);
  }
}
