package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class RedisOtpChallengeStateAdapter implements OtpChallengeStatePort {

  private static final String OTP_CAPABILITY = "auth";
  private static final String OTP_NAMESPACE = "otp";

  private final CachePort cachePort;
  private final RedisCacheKeyFactory keyFactory;
  private final RedisOtpChallengeJsonMapper jsonMapper;
  private final Clock clock;

  public RedisOtpChallengeStateAdapter(
      CachePort cachePort,
      RedisCacheKeyFactory keyFactory,
      RedisOtpChallengeJsonMapper jsonMapper) {
    this(cachePort, keyFactory, jsonMapper, Clock.systemUTC());
  }

  RedisOtpChallengeStateAdapter(
      CachePort cachePort,
      RedisCacheKeyFactory keyFactory,
      RedisOtpChallengeJsonMapper jsonMapper,
      Clock clock) {
    this.cachePort = cachePort;
    this.keyFactory = keyFactory;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public Mono<Void> save(OtpChallenge challenge) {
    return Mono.defer(() -> persist(challenge));
  }

  @Override
  public Mono<Optional<OtpChallenge>> findById(String otpChallengeId) {
    return Mono.defer(
        () -> {
          if (otpChallengeId == null || otpChallengeId.isBlank()) {
            return Mono.error(new IllegalArgumentException("OTP challenge ID must not be blank"));
          }
          return cachePort
              .get(keyFor(otpChallengeId))
              .map(optionalJson -> optionalJson.map(jsonMapper::fromJson));
        });
  }

  @Override
  public Mono<Void> update(OtpChallenge challenge) {
    return Mono.defer(() -> persist(challenge));
  }

  @Override
  public Mono<Void> delete(String otpChallengeId) {
    return Mono.defer(
        () -> {
          if (otpChallengeId == null || otpChallengeId.isBlank()) {
            return Mono.error(new IllegalArgumentException("OTP challenge ID must not be blank"));
          }
          return cachePort.evict(keyFor(otpChallengeId)).then();
        });
  }

  private Mono<Void> persist(OtpChallenge challenge) {
    if (challenge == null) {
      return Mono.error(new IllegalArgumentException("OTP challenge must not be null"));
    }
    Duration ttl = ttlUntil(challenge.expiresAt());
    return cachePort.set(keyFor(challenge.otpChallengeId()), jsonMapper.toJson(challenge), ttl);
  }

  private String keyFor(String otpChallengeId) {
    return keyFactory.key(OTP_CAPABILITY, OTP_NAMESPACE, otpChallengeId);
  }

  private Duration ttlUntil(Instant expiresAt) {
    if (expiresAt == null) {
      throw new IllegalArgumentException("OTP challenge expiresAt must not be null");
    }
    Duration ttl = Duration.between(Instant.now(clock), expiresAt);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("OTP challenge TTL must be positive");
    }
    return ttl;
  }
}
