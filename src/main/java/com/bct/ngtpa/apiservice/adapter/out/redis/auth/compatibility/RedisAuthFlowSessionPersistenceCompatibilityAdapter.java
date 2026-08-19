package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public final class RedisAuthFlowSessionPersistenceCompatibilityAdapter
    implements AuthFlowSessionPersistenceCompatibilityPort {
  public static final int SUPPORTED_VERSION = 2;
  private final ReactiveStringRedisTemplate redis;
  private final String markerKey;

  public RedisAuthFlowSessionPersistenceCompatibilityAdapter(
      ReactiveStringRedisTemplate redis, RedisCacheKeyFactory keyFactory) {
    this.redis = Objects.requireNonNull(redis, "redis must not be null");
    this.markerKey =
        Objects.requireNonNull(keyFactory, "keyFactory must not be null")
            .key("auth-flow-session", "persistence-schema-version");
  }

  @Override
  public Mono<AuthFlowSessionPersistenceCompatibilityResult> establish(int configuredVersion) {
    if (configuredVersion != SUPPORTED_VERSION) {
      return Mono.just(
          result(
              AuthFlowSessionPersistenceCompatibilityStatus.CONFIGURATION_VERSION_UNSUPPORTED,
              configuredVersion));
    }
    return redis
        .opsForValue()
        .get(markerKey)
        .flatMap(value -> evaluateExisting(value))
        .switchIfEmpty(initialize())
        .onErrorReturn(
            result(AuthFlowSessionPersistenceCompatibilityStatus.STORAGE_UNAVAILABLE, null));
  }

  private Mono<AuthFlowSessionPersistenceCompatibilityResult> initialize() {
    return redis
        .opsForValue()
        .setIfAbsent(markerKey, Integer.toString(SUPPORTED_VERSION))
        .flatMap(
            initialized ->
                Boolean.TRUE.equals(initialized)
                    ? Mono.just(
                        result(
                            AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZED,
                            SUPPORTED_VERSION))
                    : redis
                        .opsForValue()
                        .get(markerKey)
                        .flatMap(this::evaluateExisting)
                        .switchIfEmpty(
                            Mono.just(
                                result(
                                    AuthFlowSessionPersistenceCompatibilityStatus
                                        .INITIALIZATION_CONFLICT,
                                    null))));
  }

  private Mono<AuthFlowSessionPersistenceCompatibilityResult> evaluateExisting(String value) {
    if (value == null || value.isBlank()) {
      return Mono.just(result(AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT, null));
    }
    try {
      int actual = Integer.parseInt(value);
      return Mono.just(
          result(
              actual == SUPPORTED_VERSION
                  ? AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE
                  : AuthFlowSessionPersistenceCompatibilityStatus.EXISTING_VERSION_MISMATCH,
              actual));
    } catch (NumberFormatException exception) {
      return Mono.just(result(AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT, null));
    }
  }

  private static AuthFlowSessionPersistenceCompatibilityResult result(
      AuthFlowSessionPersistenceCompatibilityStatus status, Integer actual) {
    return new AuthFlowSessionPersistenceCompatibilityResult(status, SUPPORTED_VERSION, actual);
  }
}
